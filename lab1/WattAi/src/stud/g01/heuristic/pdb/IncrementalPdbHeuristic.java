package stud.g01.heuristic.pdb;

import java.util.*;
import core.problem.State;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** 同一目标的多个完整加性分组取max；可逆零代价移动不改变未收费组的距离。 */
final class IncrementalPdbHeuristic implements PackedPuzzleHeuristic {
    private final PuzzleBoard goal;
    private final DensePatternTable[] tables;
    private final int[] offsets,groupByTile,slotByTile,transposeTile;
    private final int size,orientations,families;

    IncrementalPdbHeuristic(boolean symmetry,DisjointPatternDatabase[] sources){
        if(sources==null||sources.length==0)throw new IllegalArgumentException("At least one PDB required");
        goal=sources[0].goalBoard();size=goal.size();
        orientations=symmetry&&goal.blankIndex()/size==goal.blankIndex()%size?2:1;
        families=sources.length*orientations;
        ArrayList<DensePatternTable> list=new ArrayList<>();offsets=new int[sources.length+1];
        for(int i=0;i<sources.length;i++){
            if(!goal.equals(sources[i].goalBoard()))throw new IllegalArgumentException("PDB target mismatch");
            offsets[i]=list.size();Collections.addAll(list,sources[i].tables());
        }
        offsets[sources.length]=list.size();tables=list.toArray(DensePatternTable[]::new);
        groupByTile=new int[sources.length*16];slotByTile=new int[sources.length*16];
        for(int db=0;db<sources.length;db++)for(int g=offsets[db];g<offsets[db+1];g++)
            for(int s=0;s<tables[g].pattern.length;s++){
                int t=tables[g].pattern[s];groupByTile[db*16+t]=g;slotByTile[db*16+t]=s+1;
            }
        transposeTile=new int[16];int[] targetLocations=DensePatternTable.locations(goal);
        for(int t=0;t<size*size;t++)transposeTile[t]=goal.tileAt(transpose(targetLocations[t]));
    }
    private int transpose(int p){return (p%size)*size+p/size;}
    @Override public int heuristics(State state,State requestedGoal){
        if(!(state instanceof PuzzleBoard)||!goal.equals(requestedGoal))throw new IllegalArgumentException("PDB target/type mismatch");
        PuzzleBoard board=(PuzzleBoard)state;
        if(board.size()!=size)throw new IllegalArgumentException("Board size mismatch");
        int[] locations=DensePatternTable.locations(board);int best=0;
        for(int o=0;o<orientations;o++){
            int[] projected=locations;
            if(o==1){projected=new int[locations.length];for(int t=0;t<locations.length;t++)projected[transposeTile[t]]=transpose(locations[t]);}
            for(int db=0;db<offsets.length-1;db++){
                int sum=0;for(int g=offsets[db];g<offsets[db+1];g++)sum+=tables[g].estimate(projected);
                best=Math.max(best,sum);
            }
        }
        return best;
    }
    @Override public Cursor cursor(PuzzleBoard initial){
        if(initial==null||initial.size()!=size)throw new IllegalArgumentException("Board size mismatch");
        return new IncrementalCursor(initial);
    }
    private final class IncrementalCursor implements Cursor {
        private final long[] keys=new long[tables.length*orientations];
        private final int[] values=new int[keys.length],sums=new int[families];
        private final long[][] oldKeys=new long[128][families];
        private final int[][] oldValues=new int[128][families],indices=new int[128][families];
        private int depth;
        IncrementalCursor(PuzzleBoard initial){
            int[] locations=DensePatternTable.locations(initial);
            for(int o=0;o<orientations;o++){
                int[] projected=locations;
                if(o==1){projected=new int[locations.length];for(int t=0;t<locations.length;t++)projected[transposeTile[t]]=transpose(locations[t]);}
                for(int db=0;db<offsets.length-1;db++)for(int g=offsets[db];g<offsets[db+1];g++){
                    int index=o*tables.length+g;
                    keys[index]=Integer.toUnsignedLong(tables[g].pack(projected));
                    values[index]=tables[g].estimateKey(keys[index]);sums[db*orientations+o]+=values[index];
                }
            }
        }
        @Override public int value(){int best=0;for(int sum:sums)best=Math.max(best,sum);return best;}
        @Override public void move(int from,int to,int tile){
            if(depth>=oldKeys.length)throw new IllegalStateException("PDB cursor depth exceeded");
            for(int db=0;db<offsets.length-1;db++)for(int o=0;o<orientations;o++){
                int mapped=o==0?tile:transposeTile[tile],a=o==0?from:transpose(from),b=o==0?to:transpose(to);
                int group=groupByTile[db*16+mapped],slot=slotByTile[db*16+mapped];
                int index=o*tables.length+group,family=db*orientations+o;
                indices[depth][family]=index;oldKeys[depth][family]=keys[index];oldValues[depth][family]=values[index];
                long key=(keys[index]&~15L)|b;
                key=(key&~(15L<<(4*slot)))|((long)a<<(4*slot));
                keys[index]=key;int next=tables[group].estimateKey(key);
                sums[family]+=next-values[index];values[index]=next;
            }
            depth++;
        }
        @Override public void undo(){
            if(depth==0)throw new IllegalStateException("Cannot undo root cursor");depth--;
            for(int family=0;family<families;family++){
                int index=indices[depth][family];sums[family]+=oldValues[depth][family]-values[index];
                keys[index]=oldKeys[depth][family];values[index]=oldValues[depth][family];
            }
        }
    }
}
