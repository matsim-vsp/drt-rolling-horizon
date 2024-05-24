package org.matsim.project.drtOperationStudy.run.modules;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.QSimScopeForkJoinPoolHolder;
import org.matsim.contrib.drt.optimizer.VehicleDataEntryFactoryImpl;
import org.matsim.contrib.drt.optimizer.VehicleEntry;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.project.drtOperationStudy.rollingHorizon.PDPTWSolverJsprit;
import org.matsim.project.drtOperationStudy.rollingHorizon.RollingHorizonDrtOptimizer;

public class RollingHorizonModule extends AbstractDvrpModeQSimModule {
    private final DrtConfigGroup drtConfigGroup;
    private final double horizon;
    private final double interval;
    private final PDPTWSolverJsprit.Options options;

    public RollingHorizonModule(DrtConfigGroup drtConfigGroup, double horizon, double interval, PDPTWSolverJsprit.Options options) {
        super(drtConfigGroup.mode);
        this.drtConfigGroup = drtConfigGroup;
        this.horizon = horizon;
        this.interval = interval;
        this.options = options;
    }

    @Override
    protected void configureQSim() {
        addModalComponent(DrtOptimizer.class, modalProvider(getter -> new RollingHorizonDrtOptimizer(drtConfigGroup,
                getter.getModal(Network.class), getter.getModal(TravelTime.class),
                getter.getModal(TravelDisutilityFactory.class).createTravelDisutility(getter.getModal(TravelTime.class)),
                getter.get(MobsimTimer.class), getter.getModal(DrtTaskFactory.class), getter.get(EventsManager.class), getter.getModal(Fleet.class),
                getter.getModal(ScheduleTimingUpdater.class), getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(),
                getter.getModal(VehicleEntry.EntryFactory.class),
                getter.get(PDPTWSolverJsprit.class), getter.get(Population.class), horizon, interval)));

        bind(PDPTWSolverJsprit.class).toProvider(modalProvider(
                getter -> new PDPTWSolverJsprit(drtConfigGroup, getter.get(Network.class), options)));

        addModalComponent(QSimScopeForkJoinPoolHolder.class,
                () -> new QSimScopeForkJoinPoolHolder(drtConfigGroup.numberOfThreads));
        bindModal(VehicleEntry.EntryFactory.class).toInstance(new VehicleDataEntryFactoryImpl(drtConfigGroup));

    }
}
