package org.matsim.project.drtOperationStudy.analysis;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.QSimFreeSpeedTravelTime;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DirectTripNetworkCountData implements MATSimAppCommand {

    @CommandLine.Option(names = "--config", description = "input config", required = true)
    private String inputConfig;

    @CommandLine.Option(names = "--output", description = "output folder", required = true)
    private String output;

    private static final Logger log = LogManager.getLogger(DrtRequestsPreAnalysis.class);

    public static void main(String[] args) {
        new DirectTripNetworkCountData().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.loadConfig(inputConfig);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();
        Population plans = scenario.getPopulation();

        TravelTime travelTime = new QSimFreeSpeedTravelTime(config.qsim().getTimeStepSize());
        TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
        LeastCostPathCalculator router = new SpeedyALTFactory().createPathCalculator(network, travelDisutility, travelTime);

        Map<Id<Link>, MutableInt> countMap = new HashMap<>();

        int totalCount = 0;
        for (Person person : plans.getPersons().values()) {
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                Link fromLink = network.getLinks().get(trip.getOriginActivity().getLinkId());
                Link toLink = network.getLinks().get(trip.getDestinationActivity().getLinkId());
                double departureTime = trip.getOriginActivity().getEndTime().seconds();

                LeastCostPathCalculator.Path path =
                        router.calcLeastCostPath(fromLink.getToNode(), toLink.getFromNode(), departureTime, null, null);
                path.links.add(toLink);

                for (Link link : path.links) {
                    countMap.computeIfAbsent(link.getId(), l -> new MutableInt()).increment();
                    totalCount++;
                }

            }
        }

        for (Link link : network.getLinks().values()) {
            int dailyCount = countMap.getOrDefault(link.getId(), new MutableInt()).intValue();
            link.getAttributes().putAttribute("DirectTripCount", dailyCount);
            link.getAttributes().putAttribute("DirTripCntNorm", (double) dailyCount / (double) totalCount);
        }

        Files.createDirectories(Path.of(output));
        new NetworkWriter(network).write(output + "/network-with-count.xml.gz");

        return 0;
    }


}
