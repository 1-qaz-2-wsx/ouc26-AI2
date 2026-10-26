package stud.g01.heuristic.pdb;

import core.solver.algorithm.heuristic.Predictor;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** 正式不相交加性PDB；构建/加载一次，查询仅访问内存。实例只读，可共享Predictor。 */
public final class DisjointPatternDatabase {
    private static final int MAGIC=0x44504442, VERSION=2;
    private final PuzzleBoard goal;
    private final DensePatternTable[] tables;

    private DisjointPatternDatabase(PuzzleBoard goal,int[][] groups) {
        if(goal==null||groups==null||groups.length==0) throw new IllegalArgumentException("Goal/groups required");
        this.goal=goal;
        boolean[] used=new boolean[goal.size()*goal.size()];
        tables=new DensePatternTable[groups.length];
        for(int i=0;i<groups.length;i++) {
            int[] group=groups[i];
            if(group==null||group.length==0||group.length>5) throw new IllegalArgumentException("Group size must be 1..5");
            for(int tile:group){
                if(tile<=0||tile>=used.length||used[tile]) throw new IllegalArgumentException("Invalid/overlapping pattern");
                used[tile]=true;
            }
            tables[i]=new DensePatternTable(used.length,group);
        }
        for(int tile=1;tile<used.length;tile++) if(!used[tile]) throw new IllegalArgumentException("Patterns must cover all numbered tiles");
    }

    public static DisjointPatternDatabase build(PuzzleBoard goal,int[]... groups) {
        var db=new DisjointPatternDatabase(goal,groups);
        for(var table:db.tables) table.build(goal);
        return db;
    }

    private static int[][] defaults(PuzzleBoard goal) {
        if(goal==null) throw new IllegalArgumentException("Goal required");
        return goal.size()==4 ? new int[][]{{1,2,3,4,5},{6,7,8,9,10},{11,12,13,14,15}}
                : new int[][]{{1,2,3},{4,5,6},{7,8}};
    }

    /** 缺失时才构建；已有损坏文件抛IOException，调用者不能将异常当无解。 */
    public static DisjointPatternDatabase loadOrBuild(PuzzleBoard goal,Path directory) throws IOException {
        Path file=cachePath(goal,directory);
        if(Files.exists(file)) return load(goal,directory);
        var db=build(goal,defaults(goal)); db.save(directory); return db;
    }

    public int estimate(PuzzleBoard board) {
        if(board==null||board.size()!=goal.size()) throw new IllegalArgumentException("Board size mismatch");
        int[] location=DensePatternTable.locations(board);
        int result=0;
        for(var table:tables) result+=table.estimate(location);
        return result;
    }

    public Predictor predictor() {
        return (state,requestedGoal)->{
            if(!(state instanceof PuzzleBoard)||!goal.equals(requestedGoal)) throw new IllegalArgumentException("PDB target/type mismatch");
            return estimate((PuzzleBoard)state);
        };
    }

    public long distanceBytes(){long result=0;for(var table:tables)result+=table.distances.length;return result;}
    public PuzzleBoard goalBoard(){return goal;}

    /** 单文件完整快照，先写临时文件，再替换；支持自定义分组保存/加载。 */
    public void save(Path directory) throws IOException {
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(goal.size());
            for(int tile:goal.toArray()) out.writeInt(tile);
            out.writeInt(tables.length);
            for(var table:tables){
                out.writeInt(table.pattern.length);
                for(int tile:table.pattern)out.writeInt(tile);
                out.writeInt(table.distances.length);out.write(table.distances);
            }
        }
        byte[] bytes=buffer.toByteArray();CRC32 crc=new CRC32();crc.update(bytes);
        int[][] groups=Arrays.stream(tables).map(t->t.pattern).toArray(int[][]::new);
        Path target=file(goal,directory,groups).toAbsolutePath();Files.createDirectories(target.getParent());
        Path temporary=Files.createTempFile(target.getParent(),"pdb-",".tmp");
        try {
            try(var out=new DataOutputStream(Files.newOutputStream(temporary))){out.write(bytes);out.writeLong(crc.getValue());}
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }

    public static DisjointPatternDatabase load(PuzzleBoard goal,Path directory) throws IOException {
        return load(goal,directory,defaults(goal));
    }

    /** 自定义分组必须显式指定；默认load只接受默认完整分组。 */
    public static DisjointPatternDatabase load(PuzzleBoard goal,Path directory,int[]... requestedGroups) throws IOException {
        // 在分配读取缓冲区之前校验调用方请求。
        var db=new DisjointPatternDatabase(goal,requestedGroups);
        Path path=file(goal,directory,requestedGroups);
        long length=Files.size(path);
        if(length<64||length>100_000_000)throw new IOException("Invalid PDB length");
        byte[] bytes=Files.readAllBytes(path);
        if(bytes.length!=length)throw new IOException("PDB changed while loading");
        CRC32 crc=new CRC32();crc.update(bytes,0,bytes.length-8);
        if(crc.getValue()!=ByteBuffer.wrap(bytes,bytes.length-8,8).getLong())throw new IOException("PDB CRC32 mismatch");
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes,0,bytes.length-8))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION||in.readInt()!=goal.size())throw new IOException("PDB format mismatch");
            for(int tile:goal.toArray())if(in.readInt()!=tile)throw new IOException("PDB goal mismatch");
            int count=in.readInt();if(count<1||count>15)throw new IOException("Invalid group count");
            int[][] groups=new int[count][];byte[][] data=new byte[count][];
            for(int i=0;i<count;i++){
                int n=in.readInt();if(n<1||n>5)throw new IOException("Invalid group size");
                groups[i]=new int[n];for(int j=0;j<n;j++)groups[i][j]=in.readInt();
                int expected=1;for(int j=0;j<=n;j++)expected*=goal.size()*goal.size()-j;
                if(in.readInt()!=expected)throw new IOException("Invalid table size");
                data[i]=in.readNBytes(expected);if(data[i].length!=expected)throw new EOFException();
                for(byte value:data[i])if(Byte.toUnsignedInt(value)>80)throw new IOException("Invalid distance");
            }
            if(in.available()!=0)throw new IOException("Trailing data");
            if(!Arrays.deepEquals(groups,requestedGroups))throw new IOException("PDB partition mismatch");
            for(int i=0;i<count;i++)System.arraycopy(data[i],0,db.tables[i].distances,0,data[i].length);
            if(db.estimate(goal)!=0)throw new IOException("Nonzero goal heuristic");
            return db;
        }catch(IllegalArgumentException ex){throw new IOException("Invalid PDB metadata",ex);}
    }

    public static Path cachePath(PuzzleBoard goal,Path directory) {
        return file(goal,directory,defaults(goal));
    }

    private static Path file(PuzzleBoard goal,Path directory,int[][] groups) {
        if(goal==null||directory==null)throw new IllegalArgumentException("Goal/directory required");
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((goal.size()+Arrays.toString(goal.toArray())+Arrays.deepToString(groups)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return directory.resolve("disjoint-v2-"+HexFormat.of().formatHex(digest,0,12)+".pdb");
        }catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
}
