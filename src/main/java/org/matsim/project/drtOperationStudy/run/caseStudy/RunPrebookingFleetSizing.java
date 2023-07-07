package org.matsim.project.drtOperationStudy.run.caseStudy;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.drt.extension.preplanned.run.PreplannedDrtControlerCreator;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.project.drtOperationStudy.analysis.DrtPerformanceQuantification;
import org.matsim.project.drtOperationStudy.analysis.FleetSizingProblemAnalysis;
import org.matsim.project.drtOperationStudy.rollingHorizon.PDPTWSolverJsprit;
import org.matsim.project.drtOperationStudy.run.modules.LinearStopDurationModule;
import org.matsim.project.drtOperationStudy.run.modules.RollingHorizonModule;
import org.matsim.project.utils.DvrpBenchmarkTravelTimeModuleFixedTT;
import picocli.CommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class RunPrebookingFleetSizing implements MATSimAppCommand {
    @CommandLine.Option(names = "--config", description = "path to config file", required = true)
    private Path configPath;

    @CommandLine.Option(names = "--output", description = "root output folder", required = true)
    private String output;

    @CommandLine.Option(names = "--fleet-size", description = "fleet size", defaultValue = "250")
    private int fleetSize;

    @CommandLine.Option(names = "--steps", description = "maximum number of runs", defaultValue = "1")
    private int steps;

    @CommandLine.Option(names = "--step-size", description = "number of vehicles reduced for each step", defaultValue = "5")
    private int stepSizeStageOne;

    @CommandLine.Option(names = "--step-size-2", description = "number of vehicles increased for each step", defaultValue = "1")
    private int stepSizeStageTwo;

    @CommandLine.Option(names = "--seed", description = "number of vehicles reduced for each step", defaultValue = "4711")
    private long seed;

    @CommandLine.Option(names = "--multi-threading", description = "enable multi-threading option in jsprit", defaultValue = "false")
    private boolean multiThreading;

    @CommandLine.Option(names = "--iterations", description = "number of jsprit iterations", defaultValue = "2000")
    private int iterations;

    @CommandLine.Option(names = "--horizon", description = "horizon length", defaultValue = "1800")
    private double horizon;

    @CommandLine.Option(names = "--interval", description = "re-planning interval", defaultValue = "1200")
    private int interval;

    @CommandLine.Option(names = "--plans", description = "input plans", defaultValue = "")
    private String inputPlans;

    @CommandLine.Option(names = "--network", description = "input network", defaultValue = "")
    private String inputNetwork;

    public static void main(String[] args) {
        new RunPrebookingFleetSizing().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        // Create a temporary config file in the same folder, so that multiple runs can be run in the cluster at the same time
        String temporaryConfig = CaseStudy.prepare(configPath, output);

        // Stage 1
        int minFleetSize = fleetSize - stepSizeStageOne * (steps - 1);
        while (fleetSize >= minFleetSize) {
            String outputDirectory = runSimulation(temporaryConfig);
            int rejections = FleetSizingProblemAnalysis.analyze(outputDirectory);
            if (rejections > 0) {
                break;
            }
            fleetSize -= stepSizeStageOne;
        }

        // Stage 2
        if (fleetSize > minFleetSize && stepSizeStageTwo < stepSizeStageOne && steps > 1) {
            fleetSize = fleetSize + stepSizeStageOne - stepSizeStageTwo;
            while (true) {
                String outputDirectory = runSimulation(temporaryConfig);
                int rejections = FleetSizingProblemAnalysis.analyze(outputDirectory);
                if (rejections > 0) {
                    break;
                }
                fleetSize -= stepSizeStageTwo;
            }
        }

        // Delete the temporary config file for the current run
        Files.delete(Path.of(temporaryConfig));

        return 0;
    }

    private String runSimulation(String configFileString) {
        String outputDirectory = output + "/fleet-size-" + fleetSize;
        Config config = ConfigUtils.loadConfig(configFileString, new MultiModeDrtConfigGroup(), new DvrpConfigGroup());
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);
        DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfig, config.planCalcScore(), config.plansCalcRoute());
        // Assume we only have one DRT operator
        DrtConfigGroup drtConfigGroup = multiModeDrtConfig.getModalElements().iterator().next();
        drtConfigGroup.vehiclesFile = "drt-vehicles/" + fleetSize + "-8_seater-drt-vehicles.xml";
        config.controler().setOutputDirectory(outputDirectory);
        config.global().setRandomSeed(seed);
        if (!inputPlans.equals("")) {
            config.plans().setInputFile(inputPlans);
        }
        if (!inputNetwork.equals("")) {
            config.network().setInputFile(inputNetwork);
        }

        Scenario scenario = ScenarioUtils.loadScenario(config);
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());

        Controler controler = PreplannedDrtControlerCreator.createControler(config, false);
        controler.addOverridingModule(new DvrpModule(new DvrpBenchmarkTravelTimeModuleFixedTT(0)));
        // Add rolling horizon module with PDPTWSolverJsprit
        var options = new PDPTWSolverJsprit.Options(iterations, multiThreading, new Random(seed));
        controler.addOverridingQSimModule(new RollingHorizonModule(drtConfigGroup, horizon, interval, options));
        // Add linear stop duration module
        controler.addOverridingModule(new LinearStopDurationModule(drtConfigGroup));
        controler.run();
        return outputDirectory;
    }

}
