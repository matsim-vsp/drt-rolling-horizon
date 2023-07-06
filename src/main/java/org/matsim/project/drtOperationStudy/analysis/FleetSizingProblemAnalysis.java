package org.matsim.project.drtOperationStudy.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.extension.preplanned.optimizer.WaitForStopTask;
import org.matsim.contrib.drt.util.DrtEventsReaders;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.project.drtOperationStudy.analysis.eventHandlers.RejectionStatistics;
import org.matsim.project.drtOperationStudy.analysis.eventHandlers.VehicleDrivingTimeStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.matsim.application.ApplicationUtils.globFile;

public class FleetSizingProblemAnalysis {
    private static final VehicleDrivingTimeStatistics vehicleDrivingTimeStatistics = new VehicleDrivingTimeStatistics();
    private static final RejectionStatistics rejectionStatistics = new RejectionStatistics();

    private static final List<String> TITLE_ROW = Arrays.asList(
            "fleet_size",
            "rejection",
            "number_trips_served",
            "fleet_distance",
            "fleet_empty_distance",
            "sum_direct_distance",
            "fleet_driving_time",
            "mean_in_vehicle_travel_time",
            "mean_direct_travel_time"
    );

    public static List<String> getTitleRow() {
        return TITLE_ROW;
    }

    public static int analyze(String outputDirectory) throws IOException {
        Path outputDirectoryPath = Path.of(outputDirectory);
        Path configPath = globFile(outputDirectoryPath, "*output_config.*");
        Path networkPath = globFile(outputDirectoryPath, "*output_network.*");
        Path eventPath = globFile(outputDirectoryPath, "*output_events.*");
        String mode = TransportMode.drt;

        Config config = ConfigUtils.loadConfig(configPath.toString());
        int lastIteration = config.controler().getLastIteration();

        // Read from vehicle stats
        int fleetSize = 0;
        double totalDistance = 0;
        double emptyDistance = 0;
        Path distanceStatsFile = Path.of(outputDirectory + "/drt_vehicle_stats_" + mode + ".csv");
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(distanceStatsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                fleetSize = Integer.parseInt(record.get("vehicles"));
                totalDistance = Double.parseDouble(record.get("totalDistance"));
                emptyDistance = Double.parseDouble(record.get("totalEmptyDistance"));
            }
        }

        // Analyze trips travel time
        Path folderOfLastIteration = Path.of(outputDirectory + "/ITERS/it." + lastIteration);
        Path tripsFile = globFile(folderOfLastIteration, "*drt_legs_" + mode + ".*");
        Network network = NetworkUtils.readNetwork(networkPath.toString());
        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);

        int numOfTrips = 0;
        double sumDirectDriveTime = 0;
        double sumActualRidingTime = 0;
        double sumDirectDriveDistance = 0;
        try (CSVParser parser = new CSVParser(Files.newBufferedReader(tripsFile),
                CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
            for (CSVRecord record : parser.getRecords()) {
                numOfTrips++;
                Link fromLink = network.getLinks().get(Id.createLinkId(record.get(3)));
                Link toLink = network.getLinks().get(Id.createLinkId(record.get(6)));
                double departureTime = Double.parseDouble(record.get(0));

                double estDirectDriveTime = VrpPaths.calcAndCreatePath(fromLink, toLink, departureTime, router, travelTime).getTravelTime();
                sumDirectDriveTime += estDirectDriveTime;

                double actualInVehicleTime = Double.parseDouble(record.get(11));
                sumActualRidingTime += actualInVehicleTime;

                LeastCostPathCalculator.Path path = router.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(),
                        departureTime, null, null);
                path.links.add(toLink);
                double estDirectDriveDistance = path.links.stream().map(Link::getLength).mapToDouble(l -> l).sum();
                sumDirectDriveDistance += estDirectDriveDistance;
            }
        }

        // Go through events to calculate fleet driving time and rejections
        vehicleDrivingTimeStatistics.reset(0);
        rejectionStatistics.reset(0);
        EventsManager eventsManager = EventsUtils.createEventsManager();
        eventsManager.addHandler(vehicleDrivingTimeStatistics);
        eventsManager.addHandler(rejectionStatistics);
        MatsimEventsReader eventsReader = DrtEventsReaders.createEventsReader(eventsManager, WaitForStopTask.TYPE);
        eventsReader.readFile(eventPath.toString());
        int rejections = rejectionStatistics.getRejectedRequests();
        double fleetDrivingTime = vehicleDrivingTimeStatistics.getTotalDrivingTime();

        // Write down results
        List<String> outputRow = Arrays.asList(
                String.valueOf(fleetSize),
                String.valueOf(rejections),
                String.valueOf(numOfTrips),
                String.valueOf(Math.round(totalDistance)),
                String.valueOf(Math.round(emptyDistance)),
                String.valueOf(Math.round(sumDirectDriveDistance)),
                String.valueOf(Math.round(fleetDrivingTime)),
                String.valueOf(Math.round(sumActualRidingTime / numOfTrips)),
                String.valueOf(Math.round(sumDirectDriveTime / numOfTrips))
        );

        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputDirectoryPath.getParent() + "/result-summary.tsv", true), CSVFormat.TDF);
        tsvWriter.printRecord(outputRow);
        tsvWriter.close();

        return rejections;
    }


}
