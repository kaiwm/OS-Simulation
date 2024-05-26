import java.io.*;
import java.util.Hashtable;

class Disk {

    static final int NUM_SECTORS = 2048;
    
    static final int DISK_DELAY = 80;  // 80 for Gradescope

    int id;
    
    StringBuffer sectors[] = new StringBuffer[NUM_SECTORS];

    Disk(int newId){
        this.id = newId;
    }
    
    void write(int sector, StringBuffer data){
        this.sectors[sector] = data;
        try{
            Thread.sleep(DISK_DELAY);
        }catch (Exception e) {e.printStackTrace();}
    }
    
    void printDisk(){
        for(int i=0; i<sectors.length; i++){
            if (sectors[i] != null){
                System.out.println(i + " " + sectors[i]);
            }
        }
    }

    void read(int sector, StringBuffer data){
        data.append(this.sectors[sector]);
        try{
            Thread.sleep(DISK_DELAY);
        }catch (Exception e) {e.printStackTrace();}
    }
}

class Printer {

    static final int PRINT_DELAY = 275; // 275 for Gradescope
    int id;

    Printer(int newId){
        this.id = newId;
    }

    void print(StringBuffer data){
        try{
            File f = new File("PRINTER" + this.id);
            boolean appendMode = f.exists();
            FileWriter myWriter = new FileWriter("PRINTER" + this.id, appendMode);
            myWriter.write(data.toString() + "\n");
            myWriter.close();
        }catch (Exception e) {e.printStackTrace();}
    }

}

class UserThread extends Thread{

    // Read from Useri file and create a printJobThread for each .print
    // .save and .end indicate a saving operation to the disk

    int id;
    UserThread(int newId){
        this.id = newId;
    }

    public void run(){
        try{
            FileInputStream inputStream = new FileInputStream("USER" + this.id);
            BufferedReader myReader = new BufferedReader(new InputStreamReader(inputStream));

            for (String line; (line = myReader.readLine()) != null; ){
                String[] tokens = line.split(" ");
                if (tokens[0].equals(".print")){
                    PrintJobThread newJob = new PrintJobThread(tokens[1]);
                    newJob.start();
                }else if(tokens[0].equals(".save")){
                    this.SaveFile(tokens[1], myReader);
                }
            }

            myReader.close();

        }catch (Exception e) {e.printStackTrace();}      
    }

    public void SaveFile(String name, BufferedReader myReader){
        int diskNumber = MainClass.GlobalDiskManager.request();
        int offset = MainClass.GlobalDiskManager.getNextFree(diskNumber);
        int filelength = 0;
        try{
            for (String line; (line = myReader.readLine()) != null; ){
                if (line.equals(".end")){
                    MainClass.GlobalDirectoryManager.enter(new StringBuffer(name), new FileInfo(diskNumber, offset, filelength));
                    break;
                }else{
                    MainClass.GlobalDiskManager.disks[diskNumber].write(offset + filelength, new StringBuffer(line));
                }
                filelength++;
            }
            //MainClass.GlobalDiskManager.setNextFree(diskNumber, offset+filelength); Might need to implement this
            MainClass.GlobalDiskManager.release(diskNumber);
        }catch (Exception e) {e.printStackTrace();}
    }
}

class PrintJobThread extends Thread{
    StringBuffer line; // only allowed one line to reuse for read from disk and print to printer
    FileInfo f;
    int start;
    int d;
    
    PrintJobThread(String fileToPrint)
    {
        this.line = new StringBuffer(fileToPrint);
        this.f = MainClass.GlobalDirectoryManager.lookup(line);
        this.start = f.startingSector;
        this.d = f.diskNumber;
    }

    public void run()
    {
        int p = MainClass.GlobalPrinterManager.request();
        line.setLength(0);
        for (int i=0; i<f.fileLength; i++){
            MainClass.GlobalDiskManager.disks[d].read(start+i, line);
            MainClass.GlobalPrinterManager.printers[p].print(line);
            line.setLength(0);
        }
        MainClass.GlobalPrinterManager.release(p);
    }
}

class FileInfo{
    // DirectoryManager will use this metainfo to hold info about files after saving to disk

    int diskNumber;
    int startingSector;
    int fileLength;

    FileInfo(int newDiskNumber, int newStartingSector, int newFileLength){
        this.diskNumber = newDiskNumber;
        this.startingSector = newStartingSector;
        this.fileLength = newFileLength;
    }
}

class DirectoryManager{
    // Table that knows where files are stored on disk via mapping file names to disk sectors
    private Hashtable<String, FileInfo> T;

    DirectoryManager()
    {
        T = new Hashtable<String, FileInfo>();
    }

    void enter(StringBuffer fileName, FileInfo file){
        T.put(fileName.toString(), file);
    }

    FileInfo lookup(StringBuffer fileName){
        return T.get(fileName.toString());
    }
}

class ResourceManager {
    /*A ResourceManager gives a specific Thread exclusive access rights to a resource, either a Disk or a Printer. */
    boolean isFree[];

    ResourceManager(int numberOfItems) {
        isFree = new boolean[numberOfItems];
        for (int i=0; i<isFree.length; ++i)
            isFree[i] = true;
    }

    synchronized int request() {
        while (true) {
            for (int i = 0; i < isFree.length; ++i){
                if ( isFree[i] ) {
                    isFree[i] = false;
                    return i;
                }
                try{
                    this.wait(); // block until someone releases Resource
                } catch (Exception e) {e.printStackTrace();}
            }
        }
    }

    synchronized void release( int index ) {
        isFree[index] = true;
        this.notify(); // let a blocked thread run
    }
}

class DiskManager extends ResourceManager{
    /*The DiskManager is derived from ResourceManager and also keeps track of the next free sector on each disk, 
    which is useful for saving files.
    The DiskManager should contain the DirectoryManager for finding file sectors on Disk. */
    Disk[] disks;

    DiskManager(int numberOfItems) {
        super(numberOfItems);
        disks = new Disk[numberOfItems];
        for(int i=0; i<numberOfItems; i++){
            this.disks[i] = new Disk(i);
        }
    }

    int getNextFree(int d){
        for(int i=0; i<Disk.NUM_SECTORS; i++){
            if (this.disks[d].sectors[i] == null){
                return i;
            }
        }
        return -1;
    }

    void printDisks(){
        for (int i=0; i<disks.length; i++){
            disks[i].printDisk();
        }
    }
}

class PrinterManager extends ResourceManager{
    Printer[] printers;
    int currentPrinter;

    PrinterManager(int numberOfItems) {
        super(numberOfItems);
        printers = new Printer[numberOfItems];
        for(int i=0; i<numberOfItems; i++){
            this.printers[i] = new Printer(i);
        }
        this.currentPrinter = 0;
    }

    synchronized int request() {
        while (true) {
            for (int i = 0; i < printers.length; ++i) {
                int printerToAllocate = (currentPrinter + i) % printers.length; // Round-robin allocation
                if (isFree[printerToAllocate]) {
                    isFree[printerToAllocate] = false;
                    currentPrinter = (printerToAllocate + 1) % printers.length; // Update the current printer
                    return printerToAllocate;
                }
            }
            try {
                this.wait(); // block until someone releases a printer
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}


public class MainClass{

    public static DiskManager GlobalDiskManager;
    public static PrinterManager GlobalPrinterManager;
    public static DirectoryManager GlobalDirectoryManager;
    
    public static void main(String[] args){

        // Initialize the disk and printer managers along with their lists
        GlobalDiskManager = new DiskManager(Math.abs(Integer.parseInt(args[1])));
        GlobalPrinterManager = new PrinterManager(Math.abs(Integer.parseInt(args[2])));
        GlobalDirectoryManager = new DirectoryManager();

        UserThread[] users = new UserThread[Math.abs(Integer.parseInt(args[0]))];
        for(int i=0; i<users.length; i++){
            UserThread newUser = new UserThread(i);
            users[i] = newUser;
        }

        for(int i=0; i<users.length; i++){
            users[i].start();
        }

    }
}