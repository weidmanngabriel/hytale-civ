package dev.civilizations.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.civilizations.core.MinerWorkController.*;
import static org.junit.jupiter.api.Assertions.*;

class MinerWorkControllerTest {
    static final WorldPosition ACCESS = new WorldPosition(0.5, 10, 0.5);
    static final WorldPosition CONNECTOR = new WorldPosition(0.5, 8, 0.5);
    static final WorldPosition FRONT = new WorldPosition(5.5, 8, 0.5);
    static Task task(long id, MineNormalTaskSelector.Kind kind, int priority, int capacity) {
        return new Task(new UUID(0,id),kind,priority,capacity,new BlockPosition(5,8,0),priority==10);
    }
    static class Fixture implements Engine {
        final List<Task> tasks = new ArrayList<>();
        final Map<UUID, List<BlockPosition>> blocks = new LinkedHashMap<>();
        final Set<BlockPosition> solid = new LinkedHashSet<>();
        final Set<UUID> completed = new HashSet<>();
        final List<String> history = new ArrayList<>();
        final List<WorkIntent> performed = new ArrayList<>();
        final Set<Integer> sections = new HashSet<>();
        WorldPosition position = ACCESS;
        WorldPosition failTarget;
        Navigation forcedNavigation;
        Result result = Result.SUCCESS;
        Readiness readiness = Readiness.READY;
        Operation operation = Operation.EXCAVATE;
        String revision = "unit-0";
        int attempts;
        Task selected;
        Map<UUID,Integer> bonuses = Map.of();
        void add(Task t, int count) {
            tasks.add(t);
            List<BlockPosition> values = new ArrayList<>();
            for (int i=0;i<count;i++) values.add(new BlockPosition(i,8,(int)t.id().getLeastSignificantBits()));
            blocks.put(t.id(),values); solid.addAll(values);
        }
        @Override public WorldPosition access() { return ACCESS; }
        @Override public WorldPosition connector() { return CONNECTOR; }
        @Override public BlockPosition position() { return new BlockPosition((int)position.x(),(int)position.y(),(int)position.z()); }
        @Override public Navigation navigate(WorldPosition target) {
            history.add("move:"+target);
            if (target.equals(failTarget)) return Navigation.FAILED;
            if (forcedNavigation != null) return forcedNavigation;
            position = target; return Navigation.ARRIVED;
        }
        @Override public void stop() { history.add("stop"); }
        @Override public List<Task> tasks() { return tasks.stream().filter(t -> !completed.contains(t.id())).toList(); }
        @Override public Map<UUID,Integer> priorityBonuses() { return bonuses; }
        @Override public void priorityBonuses(Map<UUID,Integer> value) { bonuses = value; }
        @Override public Work observe(Task task) {
            if (completed.contains(task.id())) return Work.stopped(Readiness.COMPLETE);
            if (readiness != Readiness.READY) return Work.stopped(readiness);
            var candidates = blocks.getOrDefault(task.id(),List.of());
            if (operation == Operation.EXCAVATE && candidates.stream().noneMatch(solid::contains)) return Work.stopped(Readiness.COMPLETE);
            if (operation == Operation.BUILD_SECTION && sections.size()==3) return Work.stopped(Readiness.COMPLETE);
            return new Work(Readiness.READY,revision,FRONT,operation,.5,candidates,3,sections);
        }
        @Override public boolean available(BlockPosition block) { return solid.contains(block); }
        @Override public void selected(Task task) { selected=task; history.add("select:"+task.id()); }
        @Override public Result perform(WorkIntent intent) {
            attempts++; performed.add(intent); history.add("work:"+intent);
            if (result == Result.SUCCESS) {
                if (intent.operation()==Operation.EXCAVATE) assertTrue(solid.remove(intent.block()),"No double excavation");
                else if (intent.operation()==Operation.BUILD_SECTION) assertTrue(sections.add(intent.section()),"No double build");
                else completed.add(intent.taskId());
            }
            return result;
        }
        @Override public void ended(Task task, End reason) {
            history.add("end:"+task.id()+":"+reason);
            if (reason != End.DEFERRED) completed.add(task.id());
        }
    }
    @Test void entryWorkAndCompleteReturnAreOrderedAndRestDoesNotReenter() {
        var c = new MinerWorkController<String>(); var e = new Fixture();
        e.add(task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3),1);
        c.tick("a",.5,e);
        assertEquals(List.of("move:"+ACCESS,"stop","move:"+CONNECTOR,"stop","select:"+new UUID(0,1)),e.history.subList(0,5));
        assertEquals(1,e.performed.size());
        c.tick("a",.5,e);
        assertEquals(State.RESTING,c.snapshot("a").state());
        assertEquals(ACCESS,e.position);
        int moves = (int)e.history.stream().filter(s->s.startsWith("move:")).count();
        c.tick("a",.5,e);
        assertEquals(moves,e.history.stream().filter(s->s.startsWith("move:")).count());
        e.add(task(2,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3),1);
        int before = e.history.size();
        c.tick("a",.5,e);
        assertEquals("move:"+CONNECTOR,e.history.get(before));
    }
    @Test void capacityAndClaimsRemainExclusiveAcrossThreeMinersAndInterruption() {
        var c = new MinerWorkController<String>(); var e = new Fixture();
        Task task = task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3); e.add(task,4); e.result=Result.RETRY;
        for (String worker:List.of("a","b","c")) c.tick(worker,.5,e);
        assertEquals(3,c.workerCount(task));
        assertEquals(3,e.performed.stream().map(WorkIntent::block).distinct().count());
        c.tick("d",.5,e); assertEquals(State.WAITING_CAPACITY,c.snapshot("d").state());
        BlockPosition released = c.snapshot("a").claim(); c.interrupt("a");
        assertEquals(2,c.workerCount(task)); assertNull(c.snapshot("a").claim());
        c.tick("d",.5,e); assertEquals(released,c.snapshot("d").claim());
        c.forget("b"); assertEquals(2,c.workerCount(task));
    }
    @Test void interruptDiscardsTimeClaimsAndReentersAgainstCurrentWork() {
        var c = new MinerWorkController<String>(); var e = new Fixture(); e.add(task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3),2);
        c.tick("a",.25,e); assertEquals(.25,c.snapshot("a").elapsed());
        c.interrupt("a"); e.revision="unit-1"; e.history.clear();
        c.tick("a",.25,e);
        assertEquals("move:"+ACCESS,e.history.getFirst());
        assertEquals(0,e.attempts); assertEquals(.25,c.snapshot("a").elapsed());
        c.tick("a",.25,e); assertEquals(1,e.attempts);
    }
    @Test void noWorkOccursBeforeArrivalAndTerminalNavigationReleasesReservation() {
        var c = new MinerWorkController<String>(); var e = new Fixture(); Task t=task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3);e.add(t,2);
        e.forcedNavigation=Navigation.MOVING;c.tick("a",10,e); assertEquals(0,e.attempts);assertEquals(State.ENTERING_ACCESS,c.snapshot("a").state());
        e.forcedNavigation=null;e.failTarget=FRONT;c.tick("a",10,e);
        assertEquals(0,e.attempts);assertEquals(0,c.workerCount(t));assertNull(c.snapshot("a").taskId());
        assertTrue(e.history.contains("end:"+t.id()+":"+End.NAVIGATION_FAILED));
    }
    @Test void failedEntryDoesNotSpinUntilExplicitRecovery() {
        var c = new MinerWorkController<String>();var e=new Fixture();e.failTarget=ACCESS;
        c.tick("a",1,e);e.failTarget=null;c.tick("a",1,e);
        assertEquals(State.BLOCKED,c.snapshot("a").state());
        assertEquals(1,e.history.stream().filter(s->s.startsWith("move:")).count());
        c.forget("a");c.tick("a",1,e);assertEquals(State.RESTING,c.snapshot("a").state());
    }
    @Test void retriesAreBoundedAndSuccessfulRetryKeepsSameClaim() {
        var c = new MinerWorkController<String>();var e=new Fixture();Task t=task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3);e.add(t,2);e.result=Result.RETRY;
        c.tick("a",.5,e);BlockPosition claim=c.snapshot("a").claim();assertEquals(State.RETRYING,c.snapshot("a").state());
        e.result=Result.SUCCESS;c.tick("a",.5,e);assertEquals(claim,e.performed.getLast().block());
        e.result=Result.RETRY;
        for(int i=0;i<9;i++)c.tick("a",.5,e);
        assertEquals(State.BLOCKED,c.snapshot("a").state());assertEquals(0,c.workerCount(t));
        assertTrue(e.history.contains("end:"+t.id()+":"+End.UNRESOLVABLE));
    }
    @Test void staleClaimAndSemanticRevisionCannotKeepOldWorkOrElapsedTime() {
        var c=new MinerWorkController<String>();var e=new Fixture();Task t=task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3);e.add(t,3);e.result=Result.RETRY;
        c.tick("a",.5,e);var stale=c.snapshot("a").claim();e.solid.remove(stale);e.result=Result.SUCCESS;
        c.tick("a",.5,e);assertNotEquals(stale,e.performed.getLast().block());
        c.tick("a",.25,e);e.revision="next";c.tick("a",.25,e);
        assertEquals(.25,c.snapshot("a").elapsed());assertEquals("next",c.snapshot("a").revision());
    }
    @Test void priorityTenDoesNotInterruptAnActiveTaskButWinsTheNextSelection() {
        var c=new MinerWorkController<String>();var e=new Fixture();Task normal=task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3);e.add(normal,2);
        c.tick("a",.25,e);Task mandatory=task(2,MineNormalTaskSelector.Kind.INFRASTRUCTURE,10,1);e.add(mandatory,1);
        c.tick("a",.25,e);assertEquals(normal.id(),c.snapshot("a").taskId());
        e.readiness=Readiness.DEFERRED;c.tick("a",.5,e);e.readiness=Readiness.READY;c.tick("a",.25,e);
        assertEquals(mandatory.id(),c.snapshot("a").taskId());
    }
    @Test void placementAndRoomSectionWorkUseTheSameLifecycleAndKeepDistinctSections() {
        var c=new MinerWorkController<String>();var e=new Fixture();Task room=task(1,MineNormalTaskSelector.Kind.ROOM,8,2);e.add(room,0);e.operation=Operation.BUILD_SECTION;e.result=Result.RETRY;
        c.tick("a",.5,e);c.tick("b",.5,e);
        assertNotEquals(c.snapshot("a").section(),c.snapshot("b").section());
        e.result=Result.SUCCESS;c.tick("a",.5,e);c.tick("b",.5,e);c.tick("a",.5,e);
        assertEquals(Set.of(0,1,2),e.sections);
        var placement=new Fixture();placement.operation=Operation.PLACE;placement.add(task(2,MineNormalTaskSelector.Kind.INFRASTRUCTURE,7,1),0);
        c.tick("p",.5,placement);assertEquals(Operation.PLACE,placement.performed.getFirst().operation());assertNull(c.snapshot("p").taskId());
    }
    @Test void unsafeAndDeferredOutcomesReleaseEveryKindOfReservation() {
        for(var kind:MineNormalTaskSelector.Kind.values())for(var ready:List.of(Readiness.UNSAFE,Readiness.DEFERRED,Readiness.UNRESOLVABLE)){
            var c=new MinerWorkController<String>();var e=new Fixture();Task t=task(1,kind,7,1);e.add(t,1);e.readiness=ready;
            c.tick("a",.5,e);assertNull(c.snapshot("a").taskId());assertEquals(0,c.workerCount(t));
        }
    }
    @Test void identicalEngineResultHistoriesProduceGoldenDecisionsAtDifferentTickRates() {
        // Native delayed ticks vs small simulator ticks: work budgets, order and ownership agree.
        var nativeTicks=new MinerWorkController<String>();var simulationTicks=new MinerWorkController<String>();
        var a=new Fixture();var b=new Fixture();Task t=task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3);a.add(t,4);b.add(t,4);
        for(int i=0;i<4;i++){nativeTicks.tick("a",.5,a);for(int j=0;j<10;j++)simulationTicks.tick("a",.05,b);}
        assertEquals(a.performed,b.performed);assertEquals(a.solid,b.solid);
        nativeTicks.tick("a",.5,a);simulationTicks.tick("a",.05,b);
        assertEquals(nativeTicks.snapshot("a"),simulationTicks.snapshot("a"));assertEquals(ACCESS,a.position);assertEquals(ACCESS,b.position);
    }
    @Test void failureDispositionIsSharedAcrossNativeAndSyntheticWork() {
        assertEquals(Disposition.BLOCK_FRONT,disposition(task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3),End.NAVIGATION_FAILED));
        assertEquals(Disposition.ABANDON_FRONT,disposition(task(2,MineNormalTaskSelector.Kind.INFRASTRUCTURE,10,1),End.UNRESOLVABLE));
        assertEquals(Disposition.SKIP_OPTIONAL,disposition(task(3,MineNormalTaskSelector.Kind.INFRASTRUCTURE,7,1),End.NAVIGATION_FAILED));
        assertEquals(Disposition.DISABLE_ROOM,disposition(task(4,MineNormalTaskSelector.Kind.ROOM,8,3),End.UNSAFE));
    }
    @Test void nativeCoordinatorWiringAndStandaloneSimulationHaveTheSameGoldenHistory() {
        var fronts = new MineFrontCoordinator<String>();var rooms = new MineRoomCoordinator<String>();
        var reservations = new HashMap<UUID,String>();
        var nativeWiring = new MinerWorkController<>(fronts,rooms,reservations);
        var headlessWiring = new MinerWorkController<String>();
        var nativeEngine = new Fixture();var fakeEngine = new Fixture();
        for (Fixture e:List.of(nativeEngine,fakeEngine)) {
            e.add(task(1,MineNormalTaskSelector.Kind.TUNNEL_FRONT,4,3),9);e.result=Result.RETRY;
        }
        for (int step=0;step<12;step++) {
            if(step==1) { nativeWiring.interrupt("a");headlessWiring.interrupt("a"); }
            if(step==2) { nativeEngine.revision="updated";fakeEngine.revision="updated"; }
            if(step==3) { nativeEngine.result=Result.SUCCESS;fakeEngine.result=Result.SUCCESS; }
            if(step==5) { nativeEngine.readiness=Readiness.UNSAFE;fakeEngine.readiness=Readiness.UNSAFE; }
            if(step==7) {
                nativeWiring.forget("a");headlessWiring.forget("a");
                for(Fixture e:List.of(nativeEngine,fakeEngine)) {
                    e.readiness=Readiness.READY;e.operation=Operation.PLACE;
                    e.add(task(2,MineNormalTaskSelector.Kind.INFRASTRUCTURE,10,1),0);
                }
            }
            for (String worker:List.of("a","b","c")) {
                nativeWiring.tick(worker,.5,nativeEngine);headlessWiring.tick(worker,.5,fakeEngine);
                assertEquals(nativeWiring.snapshot(worker),headlessWiring.snapshot(worker));
            }
            assertEquals(nativeEngine.history,fakeEngine.history);
        }
        assertTrue(nativeEngine.history.stream().anyMatch(event->event.endsWith(":UNSAFE")));
        assertTrue(nativeEngine.performed.stream().anyMatch(intent->intent.operation()==Operation.PLACE));
        assertEquals(0,fronts.workerCount(new UUID(0,1)));assertTrue(reservations.isEmpty());
    }

}
