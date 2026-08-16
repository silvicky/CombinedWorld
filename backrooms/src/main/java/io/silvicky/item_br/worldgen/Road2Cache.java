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

import static io.silvicky.item_br.worldgen.Graphic.connect;
import static io.silvicky.item_br.worldgen.Graphic.drawLine;
import static io.silvicky.item_br.worldgen.Graphic.drawSideRect;
import static io.silvicky.item_br.worldgen.Graphic.drawSideRing;
import static io.silvicky.item_br.worldgen.Graphic.getInscribedCircle;
import static io.silvicky.item_br.worldgen.Graphic.getInscribedCircleOfCircleAndLine;
import static io.silvicky.item_br.worldgen.Graphic.getIntersection;
import static io.silvicky.item_br.worldgen.Graphic.getLineOutsideInscribedCircle;
import static io.silvicky.item_br.worldgen.Graphic.getSlopeArc;
import static io.silvicky.item_br.worldgen.Graphic.getSlopeLine;
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
                Point2[] cs = getInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), 30);
                drawSideRing(cs[1], cs[2], cs[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs[1], cs[2], cs[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs[1], cs[2], cs[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), EDGE));
                Point2[] cs2 = getLineOutsideInscribedCircle(center, ports[i].sub(center), ports[(i + 1) % 4].sub(center), cs[0], 30);
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
                Point2[] cs = getInscribedCircle(center, ports[(defect + i + 1) % 4].sub(center), ports[(defect + i + 2) % 4].sub(center), 90);
                drawSideRing(cs[2], cs[1], cs[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs[2], cs[1], cs[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs[2], cs[1], cs[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), EDGE));
            }
            //big ones, see the func call below
            boolean direction = ports[(defect + 1) % 4].sub(center).dot(ports[defect].sub(center)) > 0;
            if (direction) {
                Point2[] cs2 = getInscribedCircle(center, ports[defect].sub(center), ports[(defect + 1) % 4].sub(center), 30);
                int finalI = defect % 2;
                //missing straight line
                drawSideRect(ports[(defect + 2) % 4], cs2[1], 5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                drawSideRect(ports[(defect + 2) % 4], cs2[1], -5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                //the inner circle
                drawSideRing(cs2[1], cs2[2], cs2[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), EDGE));
                Point2[] cs3 = getInscribedCircleOfCircleAndLine(cs2[0], cs2[2], 60, true);
                //outer circle
                drawSideRing(cs2[1], cs3[2], cs2[0], 5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 * finalI, 6 - 12 * finalI, 0.5), z), EDGE));
                //transition into line
                int jointHeight = getSlopeArc(cs3[2], cs2[1], cs2[2], cs2[0], 6 * finalI, 6 - 12 * finalI, 0.5);
                drawSideRing(cs3[1], cs3[2], cs3[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs3[1], cs3[2], cs3[0], 6 - 6 * finalI, jointHeight - 6 + 6 * finalI, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs3[1], cs3[2], cs3[0], 6 - 6 * finalI, jointHeight - 6 + 6 * finalI, 0.5), z), EDGE));
            } else {
                Point2[] cs2 = getInscribedCircle(center, ports[(defect + 3) % 4].sub(center), ports[defect].sub(center), 30);
                int finalI = defect % 2;
                drawSideRect(ports[(defect + 2) % 4], cs2[2], 5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                drawSideRect(ports[(defect + 2) % 4], cs2[2], -5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                drawSideRing(cs2[1], cs2[2], cs2[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 - 6 * finalI, 12 * finalI - 6, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 - 6 * finalI, 12 * finalI - 6, 0.5), z), EDGE));
                Point2[] cs3 = getInscribedCircleOfCircleAndLine(cs2[0], cs2[1], 60, false);
                drawSideRing(cs3[2], cs2[2], cs2[0], 5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 - 6 * finalI, 12 * finalI - 6, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs2[1], cs2[2], cs2[0], 6 - 6 * finalI, 12 * finalI - 6, 0.5), z), EDGE));
                int jointHeight = getSlopeArc(cs3[2], cs2[1], cs2[2], cs2[0], 6 - 6 * finalI, 12 * finalI - 6, 0.5);
                drawSideRing(cs3[2], cs3[1], cs3[0], -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs3[2], cs3[1], cs3[0], jointHeight, 6 - 6 * finalI - jointHeight, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), cs3[2], cs3[1], cs3[0], jointHeight, 6 - 6 * finalI - jointHeight, 0.5), z), EDGE));
            }
        } else if (directions.size() == 2) {
            //connect directly
            if (directions.getLast() - directions.getFirst() == 2) {
                //straight
                int finalI = directions.getFirst();
                drawSideRect(ports[finalI + 2], ports[finalI], 5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                drawSideRect(ports[finalI + 2], ports[finalI], -5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
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
                drawSideRing(ports[p0], ports[p1], c, 5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), ports[p0], ports[p1], c, h0, h1 - h0, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), ports[p0], ports[p1], c, h0, h1 - h0, 0.5), z), EDGE));
                drawSideRing(ports[p0], ports[p1], c, -5,
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), ports[p0], ports[p1], c, h0, h1 - h0, 0.5), z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, getSlopeArc(new Point2(x, z), ports[p0], ports[p1], c, h0, h1 - h0, 0.5), z), EDGE));
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
            int finalI = i;
            //road at cross
            if (coordination[i] && coordination[i + 2]) {
                drawSideRect(ports[i + 2], ports[i], 5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                drawSideRect(ports[i + 2], ports[i], -5,
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                        (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
            }
            //connecting road
            if (coordination[i]) {
                Point2[] portsN = getNodePorts(regionPos.add(n[i][0], n[i][1]));
                try {
                    Point2[] cs = connect(ports[i], ports[i].sub(ports[i + 2]), portsN[i + 2], portsN[i + 2].sub(portsN[i]));
                    Point2 joint = cs[0].add(cs[1]).scale(0.5);
                    if (joint.sub(cs[0]).cross(ports[i].sub(cs[0])) > 0) {
                        drawSideRing(ports[i], joint, cs[0], 5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(ports[i], joint, cs[0], -5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(portsN[i + 2], joint, cs[1], 5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(portsN[i + 2], joint, cs[1], -5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                    } else {
                        drawSideRing(joint, ports[i], cs[0], 5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(joint, ports[i], cs[0], -5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(joint, portsN[i + 2], cs[1], 5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                        drawSideRing(joint, portsN[i + 2], cs[1], -5,
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                                (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                    }
                } catch (Exception e) {
                    drawSideRect(ports[i], portsN[i + 2], 5,
                            (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                            (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
                    drawSideRect(ports[i], portsN[i + 2], -5,
                            (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), ROAD),
                            (x, z) -> setBlockState(new BlockPos(x, finalI * 6, z), EDGE));
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
