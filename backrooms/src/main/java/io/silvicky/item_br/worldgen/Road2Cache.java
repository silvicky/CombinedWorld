package io.silvicky.item_br.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.silvicky.item_br.worldgen.Graphic.*;
import static io.silvicky.item_br.worldgen.RegionPos.regionSize;
import static io.silvicky.item_br.worldgen.RoadCustomRule.getNodeCoordination;

public class Road2Cache extends ChunkGenCache
{
    private static final Identifier key=Identifier.parse("silvicky:road2");

    public static BlockState ROAD = Blocks.CONCRETE.orange().defaultBlockState();

    public static BlockState EDGE = Blocks.CONCRETE.white().defaultBlockState();

    public static BlockState WALL = Blocks.CONCRETE.red().defaultBlockState();

    private final Set<RegionPos> generatedRegions=new HashSet<>();

    private static final int[][] n ={{1,0},{0,1},{-1,0},{0,-1}};

    private static final int portLength=128;

    private static final int bufferWidth=160;

    public Road2Cache(ServerLevel level, RandomState randomState)
    {
        super(0, 32, level, randomState);
    }

    private Point2[] getNeighbors(RegionPos pos)
    {
        Point2[] ret=new Point2[4];
        for(int i=0;i<4;i++)
        {
            ret[i]=getChosenPos(pos.add(n[i][0], n[i][1]));
        }
        return ret;
    }

    private Point2[] getNodePorts(RegionPos pos)
    {
        Point2[] ret=new Point2[4];
        Point2[] neighbors=getNeighbors(pos);
        Point2 center=getChosenPos(pos);
        Point2 line02=neighbors[2].sub(neighbors[0]);
        Point2 line13=neighbors[3].sub(neighbors[1]);
        ret[0]=center.sub(line02.scaleTo(portLength));
        ret[1]=center.sub(line13.scaleTo(portLength));
        ret[2]=center.add(line02.scaleTo(portLength));
        ret[3]=center.add(line13.scaleTo(portLength));
        return ret;
    }

    private Point2 getChosenPos(RegionPos regionPos)
    {
        RandomSource random=randomState.getOrCreateRandomFactory(key).at(regionPos.x,0,regionPos.z);
        return regionPos.at(random.nextInt(bufferWidth,regionSize-bufferWidth),random.nextInt(bufferWidth,regionSize-bufferWidth));
    }

    private void drawStraightRoad2(Point2 start, Point2 end, int h)
    {
        drawSideRect(start, end, 5,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
        drawSideRect(start, end, -5,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
    }

    private void drawCurvedRoad2(Arc arc, int h)
    {
        drawSideRing(arc, 5,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
        drawSideRing(arc, -5,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
    }

    private void drawCurvedRoad(Arc arc, double width, int h0, int h1)
    {
        drawCurvedRoadPartial(arc,arc,width,h0,h1);
    }

    private void drawCurvedRoadPartial(Arc arc, Arc arcRef, double width, int h0, int h1)
    {
        drawSideRing(arc, width,
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arcRef, h0, h1-h0, 0.5), z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arcRef, h0, h1-h0, 0.5), z), EDGE));
    }

    private void genRegion(RegionPos regionPos) {
        if (generatedRegions.contains(regionPos)) return;
        generatedRegions.add(regionPos);
        boolean[] coordination = getNodeCoordination(randomState, regionPos.x, regionPos.z);
        List<Integer> directions = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (coordination[i]) directions.add(i);
        Point2[] ports = getNodePorts(regionPos);
        Point2 center = getChosenPos(regionPos);
        if (directions.size() == 4) {
            //4-way interchange
            for (int i = 0; i < 4; i++) {
                int finalI = i % 2;
                Arc cs = getInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), 30);
                drawCurvedRoad(cs, -5, 6*finalI, 6-6*finalI);
                Point2[] cs2 = getLineOutsideInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), cs.center(), 30);
                drawSideRect(cs2[0], cs2[1], 5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeLine(new Point2(x, z), cs2[0], cs2[1], 6 * finalI, 6 - 12 * finalI, 0.1), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeLine(new Point2(x, z), cs2[0], cs2[1], 6 * finalI, 6 - 12 * finalI, 0.1), z), EDGE));
            }
        } else if (directions.size() == 3) {
            //3-way interchange
            int defect = 6;
            for (int i : directions) defect -= i;
            //the small ones
            for (int i = 0; i < 2; i++) {
                int finalI = (defect + i) % 2;
                Arc cs = getInscribedCircle(center, ports[(defect + i + 2) % 4].sub(center), ports[(defect + i + 1) % 4].sub(center), 90);
                drawCurvedRoad(cs, -5, 6*finalI, 6-6*finalI);
            }
            //big ones, see the func call below
            boolean direction = ports[(defect + 1) % 4].sub(center).dot(ports[defect].sub(center)) > 0;
            if (direction) {
                Arc cs2 = getInscribedCircle(center, ports[defect].sub(center), ports[(defect + 1) % 4].sub(center), 30);
                int finalI = defect % 2;
                //missing straight line
                drawStraightRoad2(ports[(defect + 2) % 4], cs2.start(), finalI * 6);
                //the inner circle
                drawCurvedRoad(cs2, -5, 6*finalI, 6-6*finalI);
                Point2[] cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.end(), 60, true);
                //outer circle
                drawCurvedRoadPartial(new Arc(cs2.center(),cs2.start(),cs3[2]), cs2, 5, 6*finalI, 6-6*finalI);
                //transition into line
                int jointHeight = getSlopeArc(cs3[2], cs2, 6 * finalI, 6 - 12 * finalI, 0.5);
                Arc xx=new Arc(cs3[0], cs3[1], cs3[2]);
                drawCurvedRoad(xx, -5, 6-6*finalI, jointHeight);
            } else {
                Arc cs2 = getInscribedCircle(center, ports[(defect + 3) % 4].sub(center), ports[defect].sub(center), 30);
                int finalI = defect % 2;
                drawStraightRoad2(ports[(defect + 2) % 4], cs2.end(), finalI * 6);
                drawCurvedRoad(cs2, -5, 6-6*finalI, 6*finalI);
                Point2[] cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.start(), 60, false);
                drawCurvedRoadPartial(new Arc(cs2.center(), cs3[2], cs2.end()), cs2, 5, 6-6*finalI, 6*finalI);
                int jointHeight = getSlopeArc(cs3[2], cs2, 6 - 6 * finalI, 12 * finalI - 6, 0.5);
                Arc xx=new Arc(cs3[0], cs3[2], cs3[1]);
                drawCurvedRoad(xx, -5, jointHeight, 6-6*finalI);
            }
        } else if (directions.size() == 2) {
            //connect directly
            if (directions.getLast() - directions.getFirst() == 2) {
                //straight
                int finalI = directions.getFirst();
                drawStraightRoad2(ports[finalI + 2], ports[finalI], finalI * 6);
            } else {
                //curve with slope
                int p0, p1;
                //it is always p0->p1, CW
                if (directions.getLast() - directions.getFirst() == 1) {
                    p0 = directions.getLast();
                    p1 = directions.getFirst();
                } else {
                    p0 = directions.getFirst();
                    p1 = directions.getLast();
                }
                Point2 c = new Point2(getIntersection(ports[p0], ports[p0 ^ 2].sub(ports[p0]), ports[p1], ports[p1 ^ 2].sub(ports[p1])));
                int h0 = (p0 % 2) * 6;
                int h1 = (p1 % 2) * 6;
                Arc arc=new Arc(c,ports[p0],ports[p1]);
                drawCurvedRoad(arc, 5, h0, h1);
                drawCurvedRoad(arc, -5, h0, h1);
            }
        } else if (directions.size() == 1) {
            //dead end
            int p = directions.getFirst();
            int y = (p % 2) * 6 + 1;
            Point2 vec = ports[p ^ 2].sub(ports[p]);
            Point2 a = ports[p].add(vec.turnLeft().scaleTo(5));
            Point2 b = ports[p].add(vec.turnLeft().scaleTo(-5));
            drawLine(a, b, (x, z) -> setBlockState(new BlockPos(x, y, z), WALL));
        }
        //public parts
        for (int i = 0; i < 2; i++) {
            //road at cross
            if (coordination[i] && coordination[i + 2]) {
                drawStraightRoad2(ports[i + 2], ports[i], i * 6);
            }
            //connecting road
            if (coordination[i]) {
                Point2[] portsN = getNodePorts(regionPos.add(n[i][0], n[i][1]));
                try {
                    Point2[] cs = connect(ports[i], ports[i].sub(ports[i + 2]), portsN[i + 2], portsN[i + 2].sub(portsN[i]));
                    Point2 joint = cs[0].add(cs[1]).scale(0.5);
                    Arc arc0,arc1;
                    if (joint.sub(cs[0]).cross(ports[i].sub(cs[0])) > 0) {
                        arc0=new Arc(cs[0],ports[i],joint);
                        arc1=new Arc(cs[1],portsN[i+2],joint);
                    } else {
                        arc0=new Arc(cs[0],joint,ports[i]);
                        arc1=new Arc(cs[1],joint,portsN[i+2]);
                    }
                    drawCurvedRoad2(arc0, i * 6);
                    drawCurvedRoad2(arc1, i * 6);
                } catch (Exception e) {
                    drawStraightRoad2(ports[i], portsN[i + 2], i * 6);
                }
            }
        }
    }

    @Override
    void genChunk(ChunkPos chunkPos)
    {
        RegionPos regionPos=RegionPos.of(chunkPos);
        genRegion(regionPos);
        genRegion(regionPos.add(-1,0));
        genRegion(regionPos.add(0,-1));
    }
}
