package dev.civilizations.simulation.prefab;

import dev.civilizations.core.*;
import dev.civilizations.simulation.MineSimulationWorld;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class MineBranchingScenarioTest {
    @Test
    void allRotationsCombineLengthsTurnsSupportsAndPreserveEveryExcavatedCell() {
        for (BuildingOrientation orientation : BuildingOrientation.values()) {
            var scenario = MineBranchingScenario.create(orientation);
            var initial = scenario.snapshot();
            var occupied = new HashSet<BlockPosition>();
            for (var segment : initial.segments()) {
                for (var p : segment.blocks()) assertTrue(occupied.add(p), "No segment overlap");
                assertFalse(segment.blocks().stream().anyMatch(p -> initial.model().cellAt(p)!=null));
            }
            assertEquals(List.of(8,12,4,5,8,9,4),
                initial.segments().stream().map(MineSegment::lengthBlocks).toList());
            scenario.runToCompletion();
            var end = scenario.snapshot();
            assertEquals("COMPLETE",end.phase());
            assertEquals(end.workplace(),end.worker(),"Final return reaches the authored entrance");
            assertTrue(end.segments().stream().allMatch(MineSegment::complete));
            assertEquals(1248,end.segments().stream().mapToInt(MineSegment::nextBlockIndex).sum());
            assertEquals(12,end.segments().stream().mapToInt(MineSegment::supportsPlaced).sum());
            Map<BlockPosition,MineSimulationWorld.Cell> expected = new HashMap<>();
            for(var segment:end.segments()) {
                segment.blocks().forEach(p->expected.put(p,MineSimulationWorld.Cell.AIR));
                for(int depth=4;depth<=segment.lengthBlocks();depth+=4)
                    for(var cell:MineSupportFrame.cells(segment,depth)) expected.put(cell.position(),
                        cell.part()==MineSupportFrame.Part.POST?MineSimulationWorld.Cell.SUPPORT_POST:MineSimulationWorld.Cell.SUPPORT_BEAM);
                assertTrue(segment.junctionBlocks().stream().allMatch(p->end.tunnelWorld().get(p)==MineSimulationWorld.Cell.AIR),
                    "Junctions remain support-free");
            }
            end.tunnelWorld().cells().forEach((p,c)->assertEquals(
                expected.getOrDefault(p,MineSimulationWorld.Cell.SOLID),c,"Unplanned rock is preserved at "+p));
        }
    }

    @Test
    void midFaceInterruptionWalksOutAndBackWithoutChangingVoxelsOrLosingProgress() {
        var scenario=MineBranchingScenario.create(BuildingOrientation.NORTH);
        var phases=new HashSet<String>();
        int guard=5000, exitSteps=0, entrySteps=0;
        boolean outside=false, otherBranch=false, resumed=false;
        while(true) {
            var before=scenario.snapshot();
            boolean advanced=scenario.step();
            var after=scenario.snapshot();
            phases.add(after.phase());
            if(!before.worker().equals(after.worker())) {
                int horizontal=Math.abs(before.worker().x()-after.worker().x())
                    +Math.abs(before.worker().z()-after.worker().z());
                assertEquals(1,horizontal,"Movement is cell-by-cell, never an exit teleport");
                assertTrue(Math.abs(before.worker().y()-after.worker().y())<=1);
                assertTrue(scenario.walkable(after.worker()),"Worker remains in an open route");
            }
            if(before.phase().equals("LEAVING_MINE")||before.phase().equals("REENTERING_MINE")
                ||before.phase().equals("FINAL_RETURN")) {
                assertEquals(before.tunnelWorld(),after.tunnelWorld(),"Return travel never excavates");
                if(before.phase().equals("LEAVING_MINE"))exitSteps++;
                if(before.phase().equals("REENTERING_MINE"))entrySteps++;
            }
            if(after.phase().equals("OUTSIDE")) {
                outside=true;assertEquals(after.workplace(),after.worker());
                assertEquals(73,after.segments().get(1).nextBlockIndex());
                assertEquals(1,after.segments().get(1).supportsPlaced());
            }
            if(after.activeSegment()==2||after.activeSegment()==3) {
                otherBranch=true;assertEquals(73,after.segments().get(1).nextBlockIndex());
            }
            if(after.phase().equals("RESUMING_SAVED_WORK")) {
                resumed=true;assertEquals(73,after.segments().get(1).nextBlockIndex());
                assertTrue(after.segments().get(2).complete());assertTrue(after.segments().get(3).complete());
            }
            if(!advanced)break;
            assertTrue(--guard>0,"Bounded scenario");
        }
        assertTrue(outside&&otherBranch&&resumed);
        assertTrue(exitSteps>10&&entrySteps>10,"Both routes are visibly traversed");
        assertTrue(phases.containsAll(Set.of("LEAVING_MINE","OUTSIDE","REENTERING_MINE",
            "RESUMING_SAVED_WORK","FINAL_RETURN","COMPLETE")));
    }
}
