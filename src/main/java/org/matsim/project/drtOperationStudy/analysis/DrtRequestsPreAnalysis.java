package org.matsim.project.drtOperationStudy.analysis;

import com.google.common.base.Preconditions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.analysis.DefaultAnalysisMainModeIdentifier;
import org.matsim.application.options.CrsOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem;
import org.matsim.contrib.drt.analysis.zonal.DrtZone;
import org.matsim.contrib.dvrp.path.VrpPath;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.*;

import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetwork;
import static org.matsim.contrib.drt.analysis.zonal.DrtGridUtils.createGridFromNetworkWithinServiceArea;
import static org.matsim.contrib.drt.analysis.zonal.DrtZonalSystem.createFromPreparedGeometries;
import static org.matsim.utils.gis.shp2matsim.ShpGeometryUtils.loadPreparedGeometries;

public class DrtRequestsPreAnalysis implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "input DRT plans", required = true)
    private String configPathString;
    @CommandLine.Option(names = "--output", description = "path to matsim output directory", required = true)
    private String outputPath;
    @CommandLine.Option(names = "--cell-size", description = "cell size for the analysis", defaultValue = "1000")
    private double cellSize;

    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();
    @CommandLine.Mixin
    private CrsOptions crs = new CrsOptions();

    private static final Logger log = LogManager.getLogger(DrtRequestsPreAnalysis.class);

    public static void main(String[] args) {
        new DrtRequestsPreAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(configPathString);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Population plans = scenario.getPopulation();
        Network network = scenario.getNetwork();
        MainModeIdentifier mainModeIdentifier = new DefaultAnalysisMainModeIdentifier();

        TravelTime travelTime = new QSimFreeSpeedTravelTime(1);
        LeastCostPathCalculator router = new SpeedyALTFactory()
                .createPathCalculator(network, new TimeAsTravelDisutility(travelTime), travelTime);

        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;

        int numOfRequests = 0;
        double sumEuclideanDistance = 0;
        double sumNetworkDistance = 0;
        double sumDirectTravelTime = 0;


        // Create Zonal system from Grid
        Map<String, PreparedGeometry> grid;
        if (shp.isDefined()) {
            URL url = shp.getShapeFile().toUri().toURL();
            List<PreparedGeometry> preparedGeometries = loadPreparedGeometries(url);
            grid = createGridFromNetworkWithinServiceArea(network, cellSize, preparedGeometries);
        } else {
            grid = createGridFromNetwork(network, cellSize);
        }
        DrtZonalSystem zonalSystem = createFromPreparedGeometries(network, grid);
        Map<String, MutableInt> departuresCount = new HashMap<>();
        Map<String, MutableInt> arrivalsCount = new HashMap<>();

        for (Person person : plans.getPersons().values()) {
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(person.getSelectedPlan());
            for (TripStructureUtils.Trip trip : trips) {
                Preconditions.checkArgument(mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(TransportMode.drt));
                Activity originAct = trip.getOriginActivity();
                Activity destinationAct = trip.getDestinationActivity();

                Link fromLink = network.getLinks().get(originAct.getLinkId());
                Link toLink = network.getLinks().get(destinationAct.getLinkId());

                DrtZone fromZone = zonalSystem.getZoneForLinkId(fromLink.getId());
                assert fromZone != null;
                departuresCount.computeIfAbsent(fromZone.getId(), n -> new MutableInt()).increment();

                DrtZone toZone = zonalSystem.getZoneForLinkId(toLink.getId());
                assert toZone != null;
                arrivalsCount.computeIfAbsent(toZone.getId(), n -> new MutableInt()).increment();

                Coord fromCoord = originAct.getCoord();
                Coord toCoord = destinationAct.getCoord();

                if (fromCoord == null) {
                    fromCoord = fromLink.getToNode().getCoord();
                }

                if (toCoord == null) {
                    toCoord = toLink.getToNode().getCoord();
                }

                // Determine the boundary rectangle
                if (fromCoord.getX() > maxX) {
                    maxX = fromCoord.getX();
                }

                if (fromCoord.getY() > maxY) {
                    maxY = fromCoord.getY();
                }

                if (fromCoord.getX() < minX) {
                    minX = fromCoord.getX();
                }

                if (fromCoord.getY() < minY) {
                    minY = fromCoord.getY();
                }

                double euclideanDistance = CoordUtils.calcEuclideanDistance(fromCoord, toCoord);
                LeastCostPathCalculator.Path path = router.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(),
                        originAct.getEndTime().orElse(0), null, null);
                path.links.add(toLink);
                double networkDistance = path.links.stream().mapToDouble(Link::getLength).sum();
                double directTravelTime = path.travelTime + 2;

                sumEuclideanDistance += euclideanDistance;
                sumNetworkDistance += networkDistance;
                sumDirectTravelTime += directTravelTime;

                numOfRequests++;
            }
        }

        double area = (maxX - minX) / 1000 * (maxY - minY) / 1000;
        double density = numOfRequests / area;

        if (!Files.exists(Path.of(outputPath))) {
            Files.createDirectories(Path.of(outputPath));
        }

        DecimalFormat df = new DecimalFormat("0.0");
        CSVPrinter tsvWriter = new CSVPrinter(new FileWriter(outputPath + "/drt-requests-characteristics.tsv"), CSVFormat.TDF);
        tsvWriter.printRecord("number_of_requests", "rectangle_area", "request_density", "mean_euclidean_dist", "mean_network_dist", "mean_travel_time");

        List<String> outputRow = new ArrayList<>();
        outputRow.add(Integer.toString(numOfRequests));
        outputRow.add(df.format(area));
        outputRow.add(df.format(density));
        outputRow.add(df.format(sumEuclideanDistance / numOfRequests));
        outputRow.add(df.format(sumNetworkDistance / numOfRequests));
        outputRow.add(df.format(sumDirectTravelTime / numOfRequests));
        tsvWriter.printRecord(outputRow);

        tsvWriter.close();

        // Write shp file
        String coordinateSystem;
        if (shp.isDefined()) {
            coordinateSystem = shp.getShapeCrs();
        } else {
            coordinateSystem = crs.getInputCRS();
        }

        Collection<SimpleFeature> features = convertGeometriesToSimpleFeatures(coordinateSystem, zonalSystem, departuresCount, arrivalsCount, numOfRequests);
        Files.createDirectories(Path.of(outputPath + "/shp-analysis"));
        ShapeFileWriter.writeGeometries(features, outputPath + "/shp-analysis/accessibility_analysis.shp");

        return 0;
    }

    private Collection<SimpleFeature> convertGeometriesToSimpleFeatures
            (String coordinateSystem, DrtZonalSystem zonalSystem, Map<String, MutableInt> departuresCount, Map<String, MutableInt> arrivalsCount, double numRequests) {
        SimpleFeatureTypeBuilder simpleFeatureBuilder = new SimpleFeatureTypeBuilder();
        try {
            simpleFeatureBuilder.setCRS(MGC.getCRS(coordinateSystem));
        } catch (IllegalArgumentException e) {
            log.warn("Coordinate reference system \""
                    + coordinateSystem
                    + "\" is unknown! ");
        }

        simpleFeatureBuilder.setName("drtZoneFeature");
        // note: column names may not be longer than 10 characters. Otherwise, the name is cut after the 10th character and the value is NULL in QGis
        simpleFeatureBuilder.add("the_geom", Polygon.class);
        simpleFeatureBuilder.add("zoneIid", String.class);
        simpleFeatureBuilder.add("centerX", Double.class);
        simpleFeatureBuilder.add("centerY", Double.class);
        simpleFeatureBuilder.add("departures", Double.class);
        simpleFeatureBuilder.add("dep_pct", Double.class);
        simpleFeatureBuilder.add("arrivals", Double.class);
        simpleFeatureBuilder.add("arr_pct", Double.class);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(simpleFeatureBuilder.buildFeatureType());

        Collection<SimpleFeature> features = new ArrayList<>();

        for (DrtZone zone : zonalSystem.getZones().values()) {
            Object[] featureAttributes = new Object[8];
            Geometry geometry = zone.getPreparedGeometry() != null ? zone.getPreparedGeometry().getGeometry() : null;
            featureAttributes[0] = geometry;
            featureAttributes[1] = zone.getId();
            featureAttributes[2] = zone.getCentroid().getX();
            featureAttributes[3] = zone.getCentroid().getY();
            featureAttributes[4] = departuresCount.getOrDefault(zone.getId(), new MutableInt()).intValue();
            featureAttributes[5] = departuresCount.getOrDefault(zone.getId(), new MutableInt()).intValue() / numRequests * 100;
            featureAttributes[6] = arrivalsCount.getOrDefault(zone.getId(), new MutableInt()).intValue();
            featureAttributes[7] = arrivalsCount.getOrDefault(zone.getId(), new MutableInt()).intValue() / numRequests * 100;

            try {
                features.add(builder.buildFeature(zone.getId(), featureAttributes));
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }

        return features;
    }
}
