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

    private static final int portLength=144;

    private static final int bufferWidth=176;

    private static final int innerCircleRadius=30;

    private static final int largeCircleRadius=90;

    private static final int roadWidth=5;

    private static final int transitionCircleRadius=60;

    private static final int gapHeight=6;

    private static final double angleBuffer =0.5;

    private static final double linearBuffer=0.1;

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
        drawSideRect(start, end, roadWidth,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
        drawSideRect(start, end, -roadWidth,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
    }

    private void drawStraightRoad(Point2 start, Point2 end, double h0, double h1)
    {
        drawSideRect(start, end, roadWidth,
                (x, z) -> setBlockState(new BlockPos(x, getSlopeLine(new Point2(x, z), start, end, h0, h1-h0, linearBuffer), z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, getSlopeLine(new Point2(x, z), start, end, h0, h1-h0, linearBuffer), z), EDGE));
    }

    private void drawCurvedRoad2(Arc arc, int h)
    {
        drawSideRing(arc, roadWidth,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
        drawSideRing(arc, -roadWidth,
                (x, z) -> setBlockState(new BlockPos(x, h, z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, h, z), EDGE));
    }

    private void drawCurvedRoad(Arc arc, double width, double h0, double h1)
    {
        drawSideRing(arc, width,
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer), z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer), z), EDGE));
    }

    private void drawCurvedRoad(Arc arc, double width, double h0, double h1, double bufferStart, double bufferEnd)
    {
        drawSideRing(arc, width,
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd), z), ROAD),
                (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd), z), EDGE));
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
                Arc cs = getInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), innerCircleRadius);
                drawCurvedRoad(cs, -roadWidth, gapHeight*finalI, gapHeight-gapHeight*finalI);
                Point2[] cs2 = getLineOutsideInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), cs.center(), innerCircleRadius);
                drawStraightRoad(cs2[0], cs2[1], gapHeight * finalI, gapHeight - gapHeight * finalI);
            }
        } else if (directions.size() == 3) {
            //3-way interchange
            int defect = gapHeight;
            for (int i : directions) defect -= i;
            //the small ones
            for (int i = 0; i < 2; i++) {
                int finalI = (defect + i) % 2;
                Arc cs = getInscribedCircle(center, ports[(defect + i + 2) % 4].sub(center), ports[(defect + i + 1) % 4].sub(center), largeCircleRadius);
                drawCurvedRoad(cs, -roadWidth, gapHeight*finalI, gapHeight-gapHeight*finalI);
            }
            //big ones, see the func call below
            boolean direction = ports[(defect + 1) % 4].sub(center).dot(ports[defect].sub(center)) > 0;
            if (direction) {
                Arc cs2 = getInscribedCircle(center, ports[defect].sub(center), ports[(defect + 1) % 4].sub(center), innerCircleRadius);
                int finalI = defect % 2;
                //missing straight line
                drawStraightRoad2(ports[(defect + 2) % 4], cs2.start(), finalI * gapHeight);
                //the inner circle
                drawCurvedRoad(cs2, -roadWidth, gapHeight*finalI, gapHeight-gapHeight*finalI);
                Arc cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.end(), transitionCircleRadius, true);
                //outer circle
                double jointHeight = getSlopeArcD(cs3.end(), cs2, gapHeight * finalI, gapHeight - 2* gapHeight * finalI, angleBuffer, 0);
                drawCurvedRoad(new Arc(cs2.center(),cs2.start(),cs3.end(),innerCircleRadius), roadWidth, gapHeight*finalI, jointHeight, angleBuffer,  0);
                //transition into line
                drawCurvedRoad(cs3, -roadWidth, gapHeight-gapHeight*finalI, jointHeight, angleBuffer, 0);
            } else {
                Arc cs2 = getInscribedCircle(center, ports[(defect + 3) % 4].sub(center), ports[defect].sub(center), innerCircleRadius);
                int finalI = defect % 2;
                drawStraightRoad2(ports[(defect + 2) % 4], cs2.end(), finalI * gapHeight);
                drawCurvedRoad(cs2, -roadWidth, gapHeight-gapHeight*finalI, gapHeight*finalI);
                Arc cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.start(), transitionCircleRadius, false);
                double jointHeight = getSlopeArcD(cs3.start(), cs2, gapHeight - gapHeight * finalI, 2 * gapHeight * finalI - gapHeight, 0, angleBuffer);
                drawCurvedRoad(new Arc(cs2.center(), cs3.start(), cs2.end(),innerCircleRadius), roadWidth, jointHeight, gapHeight*finalI, 0, angleBuffer);
                drawCurvedRoad(cs3, -roadWidth, jointHeight, gapHeight-gapHeight*finalI, 0, angleBuffer);
            }
        } else if (directions.size() == 2) {
            //connect directly
            if (directions.getLast() - directions.getFirst() == 2) {
                //straight
                int finalI = directions.getFirst();
                drawStraightRoad2(ports[finalI + 2], ports[finalI], finalI * gapHeight);
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
                Point2d c = getIntersection(ports[p0], ports[p0 ^ 2].sub(ports[p0]), ports[p1], ports[p1 ^ 2].sub(ports[p1]));
                int h0 = (p0 % 2) * gapHeight;
                int h1 = (p1 % 2) * gapHeight;
                Arc arc=new Arc(c,ports[p0],ports[p1],c.sub(new Point2d(ports[p0])).len());
                drawCurvedRoad(arc, roadWidth, h0, h1);
                drawCurvedRoad(arc, -roadWidth, h0, h1);
            }
        } else if (directions.size() == 1) {
            //dead end
            int p = directions.getFirst();
            int y = (p % 2) * gapHeight + 1;
            Point2 vec = ports[p ^ 2].sub(ports[p]);
            Point2 a = ports[p].add(vec.turnLeft().scaleTo(roadWidth));
            Point2 b = ports[p].add(vec.turnLeft().scaleTo(-roadWidth));
            drawLine(a, b, (x, z) -> setBlockState(new BlockPos(x, y, z), WALL));
        }
        //public parts
        for (int i = 0; i < 2; i++) {
            //road at cross
            if (coordination[i] && coordination[i + 2]) {
                drawStraightRoad2(ports[i + 2], ports[i], i * gapHeight);
            }
            //connecting road
            if (coordination[i]) {
                Point2[] portsN = getNodePorts(regionPos.add(n[i][0], n[i][1]));
                try {
                    Point2d[] cs = connect(ports[i], ports[i].sub(ports[i + 2]), portsN[i + 2], portsN[i + 2].sub(portsN[i]));
                    Point2d joint = cs[0].add(cs[1]).scale(0.5);
                    Point2 jointI=new Point2(joint);
                    double r=cs[0].sub(cs[1]).len()/2;
                    Arc arc0,arc1;
                    if (joint.sub(cs[0]).cross(new Point2d(ports[i]).sub(cs[0])) > 0) {
                        arc0=new Arc(cs[0],ports[i],jointI,r);
                        arc1=new Arc(cs[1],portsN[i+2],jointI,r);
                    } else {
                        arc0=new Arc(cs[0],jointI,ports[i],r);
                        arc1=new Arc(cs[1],jointI,portsN[i+2],r);
                    }
                    drawCurvedRoad2(arc0, i * gapHeight);
                    drawCurvedRoad2(arc1, i * gapHeight);
                } catch (Exception e) {
                    drawStraightRoad2(ports[i], portsN[i + 2], i * gapHeight);
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
