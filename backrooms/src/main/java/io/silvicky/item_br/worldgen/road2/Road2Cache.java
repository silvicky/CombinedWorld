package io.silvicky.item_br.worldgen.road2;

import com.mojang.datafixers.util.Pair;
import io.silvicky.item_br.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;

import static io.silvicky.item_br.worldgen.Graphic.*;
import static io.silvicky.item_br.worldgen.RegionPos.regionSize;
import static io.silvicky.item_br.worldgen.road2.Road2Blocks.*;
import static io.silvicky.item_br.worldgen.RoadCustomRule.getNodeCoordination;
import static java.lang.Math.*;

public class Road2Cache extends ChunkGenCache<Road2Blocks>
{
    private static final Identifier key=Identifier.parse("silvicky:road2");

    private static final int[][] n ={{1,0},{0,1},{-1,0},{0,-1}};

    private static final int portLength=144;

    private static final int bufferWidth=176;

    private static final int innerCircleRadius=30;

    private static final int largeCircleRadius=80;

    private static final int roadWidth=5;

    private static final int transitionCircleRadius=60;

    private static final int gapHeight=6;

    private static final double angleBuffer =0.5;

    private static final double linearBuffer=0.1;

    private static final double bufferOutsideArc=3;

    private RoadPattern trunkPattern(AbstractSegment<?> segment, int h)
    {
        Dash l=new Dash(segment);
        Dash r=new Dash(segment);
        Dash light=new Dash(segment,1,30);
        return new RoadPattern(
                -2*roadWidth-5,
                2*roadWidth+5,
                (x,z)->setBlockState(new BlockPos(x,h,z), ROAD),
                List.of(
                        new Pair<>((double) -2*roadWidth-5,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE)),
                        new Pair<>((double) -2*roadWidth-1,(x, z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_EDGE)),
                        new Pair<>((double) -roadWidth-1,(x, z)->setBlockState(new BlockPos(x,h,z),l.get(new Point2d(x,z))?DASH:ROAD)),
                        new Pair<>(-1.0,(x,z)->setBlockState(new BlockPos(x,h,z),INTERNAL_EDGE)),
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,h,z),light.get(new Point2d(x,z))?GRASS_LIGHT:GRASS)),
                        new Pair<>(1.0,(x,z)->setBlockState(new BlockPos(x,h,z),INTERNAL_EDGE)),
                        new Pair<>((double) roadWidth+1,(x, z)->setBlockState(new BlockPos(x,h,z),r.get(new Point2d(x,z))?DASH:ROAD)),
                        new Pair<>((double) 2*roadWidth+1,(x, z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_EDGE)),
                        new Pair<>((double) 2*roadWidth+5,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE))
                ),
                List.of(
                        new Pair<>(new Pair<>(-2*roadWidth-5.0,-2*roadWidth-1.0),(x,z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_ROAD)),
                        new Pair<>(new Pair<>(-1.0,1.0),(x,z)->setBlockState(new BlockPos(x,h,z),GRASS)),
                        new Pair<>(new Pair<>(2*roadWidth+1.0,2*roadWidth+5.0),(x,z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern reducedPattern(int h)
    {
        return new RoadPattern(
                -roadWidth-4,
                roadWidth+4,
                (x,z)->setBlockState(new BlockPos(x,h,z), ROAD),
                List.of(
                        new Pair<>((double) -roadWidth-4,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE)),
                        new Pair<>((double) -roadWidth,(x, z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_EDGE)),
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,h,z),INTERNAL_EDGE)),
                        new Pair<>((double) roadWidth,(x, z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_EDGE)),
                        new Pair<>((double) roadWidth+4,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE))
                        ),
                List.of(
                        new Pair<>(new Pair<>(-roadWidth-4.0,(double)-roadWidth),(x,z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_ROAD)),
                        new Pair<>(new Pair<>((double) roadWidth,roadWidth+4.0),(x,z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern reducedPatternS(AbstractSegment<?> segment, int h)
    {
        Dash dash=new Dash(segment);
        return new RoadPattern(
                0,
                2*roadWidth+4,
                (x,z)->setBlockState(new BlockPos(x,h,z), ROAD),
                List.of(
                        new Pair<>(0.0,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE)),
                        new Pair<>((double) roadWidth,(x,z)->setBlockState(new BlockPos(x,h,z),dash.get(new Point2d(x,z))?DASH:ROAD)),
                        new Pair<>((double) 2*roadWidth,(x, z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_EDGE)),
                        new Pair<>((double) 2*roadWidth+4,(x, z)->setBlockState(new BlockPos(x,h,z),EDGE))
                ),
                List.of(
                        new Pair<>(new Pair<>((double)2*roadWidth,2*roadWidth+4.0),(x,z)->setBlockState(new BlockPos(x,h,z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern slopedPattern(Line line, double h0, double h1)
    {
        return new RoadPattern(
                0,
                roadWidth+4,
                (x,z)->setBlockState(new BlockPos(x,getSlopeLine(new Point2(x, z), line, h0, h1-h0, linearBuffer),z), ROAD),
                List.of(
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,getSlopeLine(new Point2(x, z), line, h0, h1-h0, linearBuffer),z),EDGE)),
                        new Pair<>(4.0,(x,z)->setBlockState(new BlockPos(x,getSlopeLine(new Point2(x, z), line, h0, h1-h0, linearBuffer),z),EMERGENCY_EDGE)),
                        new Pair<>((double) roadWidth+4,(x,z)->setBlockState(new BlockPos(x,getSlopeLine(new Point2(x, z), line, h0, h1-h0, linearBuffer),z),EDGE))
                        ),
                List.of(
                        new Pair<>(new Pair<>(0.0,4.0),(x,z)->setBlockState(new BlockPos(x,getSlopeLine(new Point2(x, z), line, h0, h1-h0, linearBuffer),z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern slopedPattern(Arc segment, double h0, double h1)
    {
        Dash l=new Dash(segment);
        Dash r=new Dash(segment);
        Dash light=new Dash(segment,1,30);
        return new RoadPattern(
                -2*roadWidth-5,
                2*roadWidth+5,
                (x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z), ROAD),
                List.of(
                        new Pair<>((double) -2*roadWidth-5,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EDGE)),
                        new Pair<>((double) -2*roadWidth-1,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EMERGENCY_EDGE)),
                        new Pair<>((double) -roadWidth-1,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),l.get(new Point2d(x,z))?DASH:ROAD)),
                        new Pair<>(-1.0,(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),INTERNAL_EDGE)),
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),light.get(new Point2d(x,z))?GRASS_LIGHT:GRASS)),
                        new Pair<>(1.0,(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),INTERNAL_EDGE)),
                        new Pair<>((double) roadWidth+1,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),r.get(new Point2d(x,z))?DASH:ROAD)),
                        new Pair<>((double) 2*roadWidth+1,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EMERGENCY_EDGE)),
                        new Pair<>((double) 2*roadWidth+5,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EDGE))
                ),
                List.of(
                        new Pair<>(new Pair<>(-2*roadWidth-5.0,-2*roadWidth-1.0),(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EMERGENCY_ROAD)),
                        new Pair<>(new Pair<>(-1.0,1.0),(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),GRASS)),
                        new Pair<>(new Pair<>(2*roadWidth+1.0,2*roadWidth+5.0),(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), segment, h0, h1-h0, angleBuffer),z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern slopedPatternS(Arc arc, double h0, double h1)
    {
        return new RoadPattern(
                0,
                roadWidth+4,
                (x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer),z), ROAD),
                List.of(
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer),z),EDGE)),
                        new Pair<>(4.0,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer),z),EMERGENCY_EDGE)),
                        new Pair<>(roadWidth+4.0,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer),z),EDGE))
                ),
                List.of(
                        new Pair<>(new Pair<>(0.0,4.0),(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, angleBuffer),z),EMERGENCY_ROAD))
                )
        );
    }

    private RoadPattern slopedPattern(Arc arc, double h0, double h1, double bufferStart, double bufferEnd)
    {
        return new RoadPattern(
                0,
                roadWidth+4,
                (x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd),z), ROAD),
                List.of(
                        new Pair<>(0.0,(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd),z),EDGE)),
                        new Pair<>(4.0,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd),z),EMERGENCY_EDGE)),
                        new Pair<>(roadWidth+4.0,(x, z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd),z),EDGE))
                ),
                List.of(
                        new Pair<>(new Pair<>(0.0,4.0),(x,z)->setBlockState(new BlockPos(x,getSlopeArc(new Point2(x, z), arc, h0, h1-h0, bufferStart, bufferEnd),z),EMERGENCY_ROAD))
                )
        );
    }

    private final RoadPattern sampleTrunkPattern = trunkPattern(new Line(0,0,0,0),0);

    private final RoadPattern sampleSlopedPattern = slopedPattern(new Line(0,0,0,0),0,0);

    public Road2Cache(RandomState randomState, RegionPos regionPos)
    {
        super(randomState, regionPos);
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

    private void drawStraightRoad2T(Line line, int h)
    {
        drawSideRect(line, trunkPattern(line, h));
    }

    private void drawStraightRoad2R(Line line, int h)
    {
        drawSideRect(line, reducedPattern(h));
    }

    private void drawStraightRoadS(Line line, int h, boolean flip)
    {
        RoadPattern roadPattern=reducedPatternS(line,h);
        if(flip)roadPattern=roadPattern.flip();
        drawSideRect(line, roadPattern);
    }

    private void drawStraightRoad(Line line, double h0, double h1)
    {
        drawSideRect(line, slopedPattern(line, h0, h1).flip());
    }

    private void drawCurvedRoad2T(Arc arc, int h)
    {
        drawSideRing(arc, trunkPattern(arc, h));
    }

    private void drawCurvedRoad2(Arc arc, double h0, double h1)
    {
        drawSideRing(arc, slopedPattern(arc, h0, h1));
    }

    private void drawCurvedRoad(Arc arc, double h0, double h1)
    {
        drawSideRing(arc, slopedPatternS(arc, h0, h1));
    }

    private void drawCurvedRoad(Arc arc, double h0, double h1, double bufferStart, double bufferEnd, boolean flip)
    {
        RoadPattern roadPattern=slopedPattern(arc, h0, h1, bufferStart, bufferEnd);
        if(flip)roadPattern=roadPattern.flip();
        drawSideRing(arc, roadPattern);
    }

    @Override
    protected void generate() {
        boolean[] coordination = getNodeCoordination(randomState, regionPos.x, regionPos.z);
        List<Integer> directions = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (coordination[i]) directions.add(i);
        Point2[] ports = getNodePorts(regionPos);
        Point2 center = getChosenPos(regionPos);
        if (directions.size() == 4) {
            //4-way interchange
            for (int i = 0; i < 4; i++) {
                int finalI = i % 2;
                Point2 d0=ports[i].sub(center);
                Point2 d1=ports[(i+1)%4].sub(center);
                double sinOfAngle=abs(d0.cross(d1)/d0.len()/d1.len());
                Point2d realCenter=new Point2d(center)
                        .add(new Point2d(d0).scaleTo(sampleTrunkPattern.max()/sinOfAngle))
                        .add(new Point2d(d1).scaleTo(sampleTrunkPattern.max()/sinOfAngle));
                Arc cs = getInscribedCircle(realCenter, d0, d1, innerCircleRadius);
                drawCurvedRoad(cs, gapHeight*finalI, gapHeight-gapHeight*finalI);
                //TODO this is too ugly, use curve, also don't use points
                Point2[] cs2 = getLineOutsideInscribedCircle(realCenter, d0, d1, cs.center(), innerCircleRadius, bufferOutsideArc+sampleSlopedPattern.width()*2);
                drawStraightRoad(new Line(cs2[0], cs2[1]), gapHeight * finalI, gapHeight - gapHeight * finalI);
            }
        } else if (directions.size() == 3) {
            //3-way interchange
            int defect = gapHeight;
            for (int i : directions) defect -= i;
            Line other=new Line(ports[(defect+2)%4],center);
            double dStart=other.getProgress(new Point2d(ports[(defect+2)%4]));
            //the small ones
            for (int i = 0; i < 2; i++) {
                int finalI = (defect + i) % 2;
                Point2 d0=ports[(defect + i + 2) % 4].sub(center);
                Point2 d1=ports[(defect + i + 1) % 4].sub(center);
                double sinOfAngle=abs(d0.cross(d1)/d0.len()/d1.len());
                Point2d realCenter;
                if(i==0) {
                    realCenter = new Point2d(center)
                            .add(new Point2d(d0).scaleTo(sampleTrunkPattern.max() / sinOfAngle))
                            .add(new Point2d(d1).scaleTo((sampleTrunkPattern.max()-1) / sinOfAngle));
                } else {
                    realCenter = new Point2d(center)
                            .add(new Point2d(d0).scaleTo((sampleTrunkPattern.max()-1) / sinOfAngle))
                            .add(new Point2d(d1).scaleTo(sampleTrunkPattern.max() / sinOfAngle));
                }
                Arc cs = getInscribedCircle(realCenter, d0, d1, largeCircleRadius);
                drawCurvedRoad(cs, gapHeight*finalI, gapHeight-gapHeight*finalI);
                double dEnd=other.getProgress(cs.center());
                drawStraightRoadS(new Line(other.a(), other.b(), dStart, dEnd), (defect % 2) * gapHeight, i == 0);
            }
            //big ones, see the func call below
            //this stuff is partially hard-coded
            boolean direction = ports[(defect + 1) % 4].sub(center).dot(ports[defect].sub(center)) > 0;
            if (direction) {
                Point2 d0=ports[defect].sub(center);
                Point2 d1=ports[(defect + 1) % 4].sub(center);
                double sinOfAngle=abs(d0.cross(d1)/d0.len()/d1.len());
                Point2d realCenter=new Point2d(center)
                        .add(new Point2d(d0).scaleTo((sampleTrunkPattern.max()-sampleSlopedPattern.width())/sinOfAngle));
                Arc cs2 = getInscribedCircle(realCenter, d0, d1, innerCircleRadius);
                int finalI = defect % 2;
                //missing straight line
                drawStraightRoad2R(new Line(other.a(),other.b(),dStart,other.getProgress(cs2.center())), finalI * gapHeight);
                //the inner circle
                drawCurvedRoad(cs2.move(-sampleSlopedPattern.width()), gapHeight*finalI, gapHeight-gapHeight*finalI);
                Arc cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.end(), transitionCircleRadius, true);
                //outer circle
                double jointHeight = getSlopeArcD(cs3.end(), cs2, gapHeight * finalI, gapHeight - 2* gapHeight * finalI, angleBuffer, 0);
                drawCurvedRoad(new Arc(cs2.center(),cs2.start(),cs3.end(),innerCircleRadius).move(sampleSlopedPattern.width()), gapHeight*finalI, jointHeight, angleBuffer,  0, true);
                //transition into line
                drawCurvedRoad(cs3.move(-sampleSlopedPattern.width()), gapHeight-gapHeight*finalI, jointHeight, angleBuffer, 0, false);
            } else {
                Point2 d0=ports[(defect + 3) % 4].sub(center);
                Point2 d1=ports[defect].sub(center);
                double sinOfAngle=abs(d0.cross(d1)/d0.len()/d1.len());
                Point2d realCenter=new Point2d(center)
                        .add(new Point2d(d1).scaleTo((sampleTrunkPattern.max()-sampleSlopedPattern.width())/sinOfAngle));
                Arc cs2 = getInscribedCircle(realCenter, d0, d1, innerCircleRadius);
                int finalI = defect % 2;
                drawStraightRoad2R(new Line(other.a(),other.b(),dStart,other.getProgress(cs2.center())), finalI * gapHeight);
                drawCurvedRoad(cs2.move(-sampleSlopedPattern.width()), gapHeight-gapHeight*finalI, gapHeight*finalI);
                Arc cs3 = getInscribedCircleOfCircleAndLine(cs2.center(), cs2.start(), transitionCircleRadius, false);
                double jointHeight = getSlopeArcD(cs3.start(), cs2, gapHeight - gapHeight * finalI, 2 * gapHeight * finalI - gapHeight, 0, angleBuffer);
                drawCurvedRoad(new Arc(cs2.center(), cs3.start(), cs2.end(),innerCircleRadius).move(sampleSlopedPattern.width()), jointHeight, gapHeight*finalI, 0, angleBuffer, true);
                drawCurvedRoad(cs3.move(-sampleSlopedPattern.width()), jointHeight, gapHeight-gapHeight*finalI, 0, angleBuffer, false);
            }
        } else if (directions.size() == 2) {
            //connect directly
            if (directions.getLast() - directions.getFirst() != 2) {
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
                Point2d c = getIntersection(new Point2d(ports[p0]), ports[p0 ^ 2].sub(ports[p0]), new Point2d(ports[p1]), ports[p1 ^ 2].sub(ports[p1]));
                int h0 = (p0 % 2) * gapHeight;
                int h1 = (p1 % 2) * gapHeight;
                Arc arc=new Arc(c,ports[p0],ports[p1],c.sub(new Point2d(ports[p0])).len());
                drawCurvedRoad2(arc, h0, h1);
            }
        } else if (directions.size() == 1) {
            //dead end
            int p = directions.getFirst();
            int y = (p % 2) * gapHeight;
            Line line = new Line(ports[p],center);
            new Line(line.a()-PI/2,line.dStart(),-line.b()- sampleTrunkPattern.min(),-line.b()- sampleTrunkPattern.max()).draw((x, z) -> setBlockState(new BlockPos(x, y, z), WALL));
        }
        //public parts
        for (int i = 0; i < 2; i++) {
            //road at cross
            if (coordination[i] && coordination[i + 2]) {
                drawStraightRoad2T(new Line(ports[i + 2], ports[i]), i * gapHeight);
            }
            //connecting road
            if (coordination[i]) {
                Point2[] portsN = getNodePorts(regionPos.add(n[i][0], n[i][1]));
                try {
                    Point2d[] cs = connect(ports[i], ports[i].sub(ports[i + 2]), portsN[i + 2], portsN[i + 2].sub(portsN[i]));
                    Point2d joint = cs[0].add(cs[1]).scale(0.5);
                    double r=cs[0].sub(cs[1]).len()/2;
                    Arc arc0,arc1;
                    if (joint.sub(cs[0]).cross(new Point2d(ports[i]).sub(cs[0])) > 0) {
                        arc0=new Arc(cs[0],r,new Point2d(ports[i]).sub(cs[0]).atan2(),joint.sub(cs[0]).atan2());
                        arc1=new Arc(cs[1],r,new Point2d(portsN[i+2]).sub(cs[1]).atan2(),joint.sub(cs[1]).atan2());
                    } else {
                        arc0=new Arc(cs[0],r,joint.sub(cs[0]).atan2(),new Point2d(ports[i]).sub(cs[0]).atan2());
                        arc1=new Arc(cs[1],r,joint.sub(cs[1]).atan2(),new Point2d(portsN[i+2]).sub(cs[1]).atan2());
                    }
                    drawCurvedRoad2T(arc0, i * gapHeight);
                    drawCurvedRoad2T(arc1, i * gapHeight);
                } catch (Exception e) {
                    drawStraightRoad2T(new Line(ports[i], portsN[i + 2]), i * gapHeight);
                }
            }
        }
    }

    @Override
    protected SimpleChunk<Road2Blocks> getNewChunk(ChunkPos chunkPos) {
        return new Road2Chunk();
    }
}
