package org.noisemap.core;
/***********************************
 * ANR EvalPDU
 * IFSTTAR 11_05_2011
 * @author Nicolas FORTIN, Judicaël PICAUT
 ***********************************/
import com.vividsolutions.jts.geom.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import org.grap.utilities.EnvelopeUtil;
/**
 * GridIndex is a class to speed up the query of a geometry collection and
 * to minimize the memory used for storing geometry items index.
 * @author N.Fortin J.Picaut (IFSTTAR 2011)
 */
public class QueryGridIndex implements QueryGeometryStructure {
        private int nbI = 0;
        private int nbJ = 0;
        private double cellSizeI;
        private double cellSizeJ;
        private Envelope mainEnv;
        private Map<Integer,RowsUnionClassification> gridContent = new HashMap<Integer,RowsUnionClassification>();
             
        
        
        
        public QueryGridIndex(final Envelope gridEnv, int xsubdiv, int ysubdiv) {
                super();
                mainEnv = gridEnv;
                nbJ = xsubdiv;
                nbI = ysubdiv;
                cellSizeI = mainEnv.getHeight() / nbI;
                cellSizeJ = mainEnv.getWidth() / nbJ;
        }
        /**
         * Compute the 1 dimensional index from i,j
         * @param i Row
         * @param j Column
         * @return The 1 dimensional index
         */
        private int getFlatIndex(int i, int j) {
            return j + i * nbJ;
        }
        private Envelope getCellEnv(int i, int j) {
                final double minx = mainEnv.getMinX() + cellSizeJ * j;
                final double miny = mainEnv.getMinY() + cellSizeI * i;
                return new Envelope(minx, minx + cellSizeJ, miny, miny + cellSizeI);
        }
        private void addItem(int i, int j, Integer content) {
            Integer flatIndex = getFlatIndex(i,j);
            RowsUnionClassification res=gridContent.get(flatIndex);
            if(res==null) {
                gridContent.put(flatIndex, new RowsUnionClassification(content));
            } else {
                res.addRow(content);
            }
        }
        /**
         * Convert coordinate to i,j index
         * @param coord Coordinate to convert
         * @return [i,j] array
         */
        private int[] getIndexByCoordinate(Coordinate coord) {
            Coordinate norm = new Coordinate((coord.x - mainEnv.getMinX()) / mainEnv.getWidth(),
                                             (coord.y - mainEnv.getMinY()) / mainEnv.getHeight());
           return new int[] {(int)Math.floor(norm.x*nbJ),(int)Math.floor(norm.y*nbI)};
        }
        private int[] getRange(Envelope geoEnv) {
                // Compute index intervals from envelopes
                int[] minIndex = getIndexByCoordinate(new Coordinate(geoEnv.getMinX(),geoEnv.getMinY()));
                int[] maxIndex = getIndexByCoordinate(new Coordinate(geoEnv.getMaxX(),geoEnv.getMaxY()));
                //Retrieve values and limit to boundary
                int minJ = Math.max(minIndex [0],0);
                int minI = Math.max(minIndex [1],0);
                int maxJ = Math.min(maxIndex [0] + 1,nbJ);
                int maxI = Math.min(maxIndex [1] + 1,nbI);
                int[] range = { minI, maxI, minJ, maxJ };
                return range;
        }
        
        @Override
        public void appendGeometry(final Geometry newGeom, final Integer externalId) {
                // Compute index intervals from envelopes
                int[] ranges = getRange(newGeom.getEnvelopeInternal());
                int minI = ranges[0], maxI = ranges[1], minJ = ranges[2], maxJ = ranges[3];
               
                GeometryFactory factory = new GeometryFactory();
                //Compute intersection between the geom and grid cells
                for (int i = minI; i < maxI; i++) {
                        for (int j = minJ; j < maxJ; j++) {
                                Envelope cellEnv = getCellEnv(i, j);
                                //Intersection of geometries is more
                                //precise than the intersection of envelope of geometry
                                //but it take more time
                                Polygon square = factory.createPolygon(
                                                (LinearRing) EnvelopeUtil.toGeometry(cellEnv), null);
                                if (square.intersects(newGeom)) {
                                        addItem(i, j, externalId);
                                }
                        }
                }
        }
        
        /**
         * @return The number of items in the grid
         */
        public int size() {
            int nbitem = 0;
            for(RowsUnionClassification item : gridContent.values()) {
                nbitem+=item.getItemCount();
            }
            return nbitem;
        }
                @Override
        public Iterator<Integer> query(Envelope queryEnv) {
            int[] ranges = getRange(queryEnv);
            int minI = ranges[0], maxI = ranges[1], minJ = ranges[2], maxJ = ranges[3];
            RowIterator querySet = new RowIterator();
            for (int i = minI; i < maxI; i++) {
                for (int j = minJ; j < maxJ; j++) {
                    Integer flatIndex = getFlatIndex(i,j);
                    RowsUnionClassification res=gridContent.get(flatIndex);
                    if(res!=null) {
                        querySet.addIntervals(res.getRowRanges());
                    }
                }
            }
            return querySet;
        }
                
        //This iterator is specific to a multiple rows union classification result
        private class RowIterator implements Iterator<Integer> {
            private RowsUnionClassification rowsIndex=null;
            private Iterator<Integer> intervalsIterator = null;
            private Integer curIntervalCursor=null;
            private Integer curIntervalEnd=null;
           
            /**
             * Add an interval array
             * [0,50,60,100] mean all integer between 0 and 50 (begin and end included),
             * then all integers between 60 and 100(begin and end included).
             * @param newInterval
             */
            public void addIntervals(Iterator<Integer> newInterval) {
                if(intervalsIterator!=null) {
                    throw new UnsupportedOperationException("Intervals can't be pushed when this iterator is used.");
                }
                while(newInterval.hasNext()) {
                    int begin = newInterval.next();
                    int end = newInterval.next();
                    if(rowsIndex==null) {
                        rowsIndex = new RowsUnionClassification(begin,end);
                    } else {
                        for(int currentIndex=begin;currentIndex<=end;currentIndex++) {
                            rowsIndex.addRow(currentIndex);
                        }
                    }
                }
            }
           
            @Override
            public boolean hasNext() {
                if(intervalsIterator==null) {
                    return !rowsIndex.isEmpty();
                } else {
                    return curIntervalCursor<curIntervalEnd || intervalsIterator.hasNext();
                }
            }
            @Override
            public Integer next() {
                //If the iterator is currently on an interval
                if(curIntervalEnd!=null && curIntervalCursor<curIntervalEnd) {
                    curIntervalCursor++;
                    return curIntervalCursor;
                    //Fetch the next interval
                }else{
                    //The interval is finished, fetch another interval
                    if(intervalsIterator==null) {
                        intervalsIterator = rowsIndex.getRowRanges();
                    }
                    if(intervalsIterator.hasNext()) {
                        //intervals always contains 2,4,6 .. items
                        curIntervalCursor=intervalsIterator.next();
                        curIntervalEnd=intervalsIterator.next();
                        return curIntervalCursor;                       
                    }else{
                        throw new NoSuchElementException("iteration has no more elements.");
                    }
                }
            }
            //User cannot remove a record
            @Override
            public void remove() {
                throw new UnsupportedOperationException("Not supported operation.");
            }           
        }
}