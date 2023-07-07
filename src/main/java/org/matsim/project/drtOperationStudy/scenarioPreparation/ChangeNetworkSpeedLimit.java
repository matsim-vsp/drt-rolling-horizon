package org.matsim.project.drtOperationStudy.scenarioPreparation;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.network.NetworkUtils;
import picocli.CommandLine;

public class ChangeNetworkSpeedLimit implements MATSimAppCommand {
    @CommandLine.Option(names = "--input", description = "input network", required = true)
    private String inputNetwork;

    @CommandLine.Option(names = "--output", description = "output network", required = true)
    private String output;

    @CommandLine.Option(names = "--speed-limit", description = "max speed limit in kph", defaultValue = "50")
    private double speedLimit;

    public static void main(String[] args) {
        new ChangeNetworkSpeedLimit().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        speedLimit /= 3.6;
        Network network = NetworkUtils.readNetwork(inputNetwork);
        for (Link link : network.getLinks().values()) {
            if (link.getFreespeed() > speedLimit) {
                link.setFreespeed(speedLimit);
            }
        }
        NetworkWriter networkWriter = new NetworkWriter(network);
        networkWriter.write(output);
        return 0;
    }
}
