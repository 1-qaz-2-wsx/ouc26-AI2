package stud.g01.heuristic.pdb;

import java.util.Arrays;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** 空格+最多5个指定数字，使用部分排列排名，而不是16的幂次稀疏数组。 */
final class DensePatternTable {
    final int cells;
    final int[] pattern;
    final byte[] distances;

    DensePatternTable(int cells, int[] pattern) {
        this.cells=cells;
        this.pattern=pattern.clone();
        int count=1;
        for(int i=0;i<=pattern.length;i++) count=Math.multiplyExact(count,cells-i);
        distances=new byte[count];
        Arrays.fill(distances,(byte)-1);
    }

    // 每个位置4bit；第0位组为空格，其余依pattern顺序。
    int pack(int[] location) {
        int packed=location[0];
        for(int i=0;i<pattern.length;i++) packed|=location[pattern[i]] << (4*(i+1));
        return packed;
    }

    private int rank(int packed) {
        int rank=0,used=0;
        for(int i=0;i<=pattern.length;i++) {
            int p=(packed >>> (4*i))&15;
            int digit=p-Integer.bitCount(used&((1<<p)-1));
            rank=rank*(cells-i)+digit;
            used|=1<<p;
        }
        return rank;
    }

    int estimate(int[] location) {
        int value=Byte.toUnsignedInt(distances[rank(pack(location))]);
        if(value==255) throw new IllegalStateException("PDB abstract state unreachable");
        return value;
    }

    void build(PuzzleBoard goal) {
        int[][] neighbors=neighbors(goal.size());
        int[] location=locations(goal);
        int root=pack(location);
        int shift=4*(pattern.length+1),mask=(1<<shift)-1;
        LongDeque queue=new LongDeque();
        distances[rank(root)]=0;
        queue.add(root,false);
        while(!queue.empty()) {
            long entry=queue.remove();
            int packed=(int)entry&mask,distance=(int)(entry>>>shift);
            if(distance!=Byte.toUnsignedInt(distances[rank(packed)])) continue;
            int blank=packed&15;
            for(int neighbor:neighbors[blank]) {
                int slot=0;
                for(int i=1;i<=pattern.length;i++) if(((packed >>>(4*i))&15)==neighbor) {slot=i;break;}
                int child=(packed&~15)|neighbor;
                if(slot!=0) child=(child&~(15<<(4*slot)))|(blank<<(4*slot));
                int candidate=distance+(slot==0?0:1),index=rank(child);
                if(candidate<Byte.toUnsignedInt(distances[index])) {
                    // 3x3/4x4任意可完成的抽象位置有不超过完整棋盘直径的距离。
                    if(candidate>=255) throw new IllegalStateException("Distance byte overflow");
                    distances[index]=(byte)candidate;
                    queue.add(Integer.toUnsignedLong(child)|((long)candidate<<shift),slot==0);
                }
            }
        }
        for(byte distance:distances) if(distance==(byte)-1) throw new IllegalStateException("Incomplete PDB");
    }

    static int[] locations(PuzzleBoard board) {
        int[] result=new int[board.size()*board.size()];
        for(int p=0;p<result.length;p++) result[board.tileAt(p)]=p;
        return result;
    }

    private static int[][] neighbors(int size) {
        int[][] result=new int[size*size][];
        for(int p=0;p<result.length;p++) {
            int[] temp=new int[4]; int n=0;
            if(p/size>0) temp[n++]=p-size;
            if(p/size<size-1) temp[n++]=p+size;
            if(p%size>0) temp[n++]=p-1;
            if(p%size<size-1) temp[n++]=p+1;
            result[p]=Arrays.copyOf(temp,n);
        }
        return result;
    }

    /** long条目避免5数字+空格24bit再附距离时出现移位/符号截断。 */
    private static final class LongDeque {
        private long[] values=new long[65536];
        private int head,size;
        boolean empty(){return size==0;}
        long remove(){long value=values[head];head=(head+1)&(values.length-1);size--;return value;}
        void add(long value,boolean first){
            if(size==values.length){
                long[] expanded=new long[Math.multiplyExact(values.length,2)];
                for(int i=0;i<size;i++) expanded[i]=values[(head+i)&(values.length-1)];
                values=expanded;head=0;
            }
            if(first){head=(head-1)&(values.length-1);values[head]=value;}
            else values[(head+size)&(values.length-1)]=value;
            size++;
        }
    }
}
