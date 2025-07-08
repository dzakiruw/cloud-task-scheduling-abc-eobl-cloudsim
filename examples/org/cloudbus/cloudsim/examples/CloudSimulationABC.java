package org.cloudbus.cloudsim.examples;

import java.util.Locale;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.DoubleStream;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHostUtilizationHistory;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicySimple;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;


/**
 * Kelas CloudSimulationABC
 * 
 * Kelas ini mengimplementasikan simulasi penjadwalan tugas di lingkungan komputasi awan
 * menggunakan algoritma Artificial Bee Colony (ABC) dengan atau tanpa peningkatan EOABC.
 * Kelas ini mensimulasikan beberapa pusat data dengan sejumlah host dan mesin virtual (VM)
 * untuk memproses beban kerja (cloudlet) dari berbagai dataset.
 * 
 * Tujuan utama adalah optimasi penjadwalan tugas untuk meminimalkan makespan dan biaya.
 */
public class CloudSimulationABC {
    
    /**
     * Inner class untuk menyimpan informasi task
     */
    private static class TaskInfo {
        public int taskId;
        public double execTime;
        
        public TaskInfo(int taskId, double execTime) {
            this.taskId = taskId;
            this.execTime = execTime;
        }
    }
    // Variabel untuk pusat data yang akan digunakan dalam simulasi
    private static PowerDatacenter datacenter1, datacenter2, datacenter3, datacenter4, datacenter5, datacenter6;
    
    // Daftar untuk menyimpan cloudlet (tugas) dan mesin virtual
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    
    // Penulis file CSV untuk menyimpan hasil simulasi
    private static BufferedWriter csvWriter;
    
    // Penulis file CSV untuk menyimpan VM statistics
    private static BufferedWriter vmCsvWriter;

    // Parameter konfigurasi simulasi
    // Apakah menggunakan peningkatan Elite Opposition-Based Artificial Bee Colony
    private static final boolean USE_EOABC = false; 
    
    // Jumlah maksimum iterasi untuk algoritma ABC
    private static final int MAX_ITERATIONS = 15;   
    
    // Ukuran populasi lebah - harus genap untuk mendukung proporsi 50/50
    private static final int POPULATION_SIZE = 30; 
    
    // Koefisien EOABC yang mengontrol tingkat oposisi
    private static final double EOABC_COEFFICIENT = 0.9; 
    
    // Jumlah percobaan yang akan dijalankan
    private static final int NUM_TRIALS = 1;      
    
    // Konfigurasi dataset
    // Jenis dataset yang digunakan: "RandSimple", "RandStratified", atau "SDSC"
    private static String DATASET_TYPE = "RandStratified"; 
    
    // Ukuran dataset untuk RandSimple dan RandStratified (dikalikan 1000)
    private static int DATASET_SIZE = 3500;
    // Dataset SDSC memiliki ukuran tetap 7395

    /**
     * Metode utama yang menjalankan simulasi
     * 
     * Metode ini mengatur parameter simulasi, menangani argumen baris perintah,
     * menjalankan beberapa uji coba simulasi, dan menulis hasil ke file CSV.
     */
    public static void main(String[] args) {
        // Mengatur locale ke English-US untuk format angka yang konsisten
        Locale.setDefault(new Locale("en", "US"));
        	
        // Memproses argumen baris perintah jika disediakan
        if (args.length >= 1) {
            String datasetArg = args[0].trim();
            if (datasetArg.equals("RandSimple") || datasetArg.equals("RandStratified") || 
                datasetArg.equals("SDSC")) {
                DATASET_TYPE = datasetArg;
                Log.printLine("Dataset type set via command line: " + DATASET_TYPE);
            } else {
                throw new IllegalArgumentException("Invalid dataset type: " + datasetArg + 
                    ". Valid options are: RandSimple, RandStratified, SDSC");
            }
            
            if (args.length >= 2 && !DATASET_TYPE.equals("SDSC")) {
                try {
                    int size = Integer.parseInt(args[1]);
                    if (size > 0) {
                        DATASET_SIZE = size;
                        Log.printLine("Dataset size set via command line: " + DATASET_SIZE);
                    }
                } catch (NumberFormatException e) {
                    Log.printLine("Invalid dataset size argument. Using default: " + DATASET_SIZE);
                }
            }
        }
        
        Log.printLine("Starting Cloud Simulation with ABC" + (USE_EOABC ? "+EOABC" : "") + " using " + DATASET_TYPE + " dataset...");

        try {
            // Menyiapkan file CSV untuk hasil simulasi
            String resultFileName = "abc_" + DATASET_TYPE + "_" + 
                                   (DATASET_TYPE.equals("SDSC") ? "7395" : DATASET_SIZE) + 
                                   "_results.csv";
            csvWriter = new BufferedWriter(new FileWriter(resultFileName));
            // Menulis header CSV untuk performance metrics
            csvWriter.write("Trial,Average Wait Time,Average Start Time,Average Execution Time,Average Finish Time,Throughput,Makespan,Imbalance Degree,Total Scheduling Length,Resource Utilization (%),Energy Consumption (kWh)\n");
            
            // Menyiapkan file CSV untuk VM statistics
            String vmStatsFileName = "abc_" + DATASET_TYPE + "_" + 
                                   (DATASET_TYPE.equals("SDSC") ? "7395" : DATASET_SIZE) + 
                                   "_vm_statistics.csv";
            vmCsvWriter = new BufferedWriter(new FileWriter(vmStatsFileName));
            // Menulis header CSV untuk VM statistics
            vmCsvWriter.write("Trial,VM ID,CPU Time,Task Count,Task Details,Min Exec Time,Max Exec Time,Avg Exec Time,Range\n");
            
            // Menjalankan beberapa percobaan simulasi
            for (int trial = 1; trial <= NUM_TRIALS; trial++) {
                Log.printLine("\n\n========== TRIAL " + trial + " OF " + NUM_TRIALS + " ==========\n");
                runSimulation(trial);
            }
            
            // Menutup file CSV setelah semua percobaan selesai
            csvWriter.close();
            Log.printLine("\nAll trials completed. Results written to " + resultFileName);
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation terminated due to an error");
            try {
                if (csvWriter != null) {
                    csvWriter.close();
                }
                if (vmCsvWriter != null) {
                    vmCsvWriter.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    
    /**
     * Metode untuk menjalankan satu simulasi
     * 
     * Metode ini menginisialisasi CloudSim, membuat pusat data, broker, VM, dan cloudlet.
     * Kemudian menjalankan algoritma ABC untuk setiap pusat data dan iterasi cloudlet,
     * dan akhirnya mencetak hasil.
     * 
     * @param trialNum Nomor percobaan saat ini
     */
    private static void runSimulation(int trialNum) throws Exception {
        // Jumlah pengguna dalam simulasi
        int num_user = 1;
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;

        // Inisialisasi CloudSim
        CloudSim.init(num_user, calendar, trace_flag);

        // Inisialisasi variabel hostId untuk pembuatan pusat data
        int hostId = 0;

        // Membuat enam pusat data dengan karakteristik yang sama
        datacenter1 = createDatacenter("DataCenter_1", hostId);
        hostId = 3;
        datacenter2 = createDatacenter("DataCenter_2", hostId);
        hostId = 6;
        datacenter3 = createDatacenter("DataCenter_3", hostId);
        hostId = 9;
        datacenter4 = createDatacenter("DataCenter_4", hostId);
        hostId = 12;
        datacenter5 = createDatacenter("DataCenter_5", hostId);
        hostId = 15;
        datacenter6 = createDatacenter("DataCenter_6", hostId);

        // Membuat broker data center yang mengelola VM dan cloudlet
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();
        int vmNumber = 54; // Jumlah total mesin virtual
        
        // Menentukan jumlah cloudlet berdasarkan jenis dataset
        int cloudletNumber;
        if (DATASET_TYPE.equals("SDSC")) {
            cloudletNumber = 7395; // SDSC memiliki ukuran tetap
        } else {
            // Untuk RandSimple dan RandStratified
            cloudletNumber = DATASET_SIZE;
        }
        
        Log.printLine("Running simulation with " + DATASET_TYPE + " dataset, " + cloudletNumber + " cloudlets");

        // Membuat VM dan cloudlet
        vmlist = createVM(brokerId, vmNumber);
        cloudletList = createCloudlet(brokerId, cloudletNumber);

        // Mengirimkan VM dan cloudlet ke broker
        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletList);

        // Menghitung jumlah iterasi cloudlet berdasarkan jumlah VM
        int cloudletLoopingNumber = cloudletNumber / vmNumber - 1;

        // Iterasi untuk setiap batch cloudlet
        for (int cloudletIterator = 0; cloudletIterator <= cloudletLoopingNumber; cloudletIterator++) {
            System.out.println("Cloudlet Iteration Number " + cloudletIterator);

            // Iterasi untuk setiap pusat data
            for (int dataCenterIterator = 1; dataCenterIterator <= 6; dataCenterIterator++) {
                
                // Parameter untuk algoritma ABC
                int Imax = MAX_ITERATIONS; // Iterasi maksimum
                int populationSize = POPULATION_SIZE; // Ukuran populasi lebah
                
                // Dalam algoritma ABC, "dimensi" mengacu pada jumlah variabel keputusan dalam solusi
                // Dalam kasus kita, setiap pusat data memproses 9 cloudlet sekaligus
                int dimensions = 9; // Setiap pusat data memproses 9 cloudlet
                
                // Parameter "limit" menentukan kapan sumber makanan harus ditinggalkan
                // Dalam literatur ABC, ini biasanya dihitung sebagai:
                // limit = 0.5 * (jumlah lebah pekerja * dimensi)
                double limit = 0.6 * (populationSize/2) * dimensions;
                
                // Koefisien EOABC (peningkatan opsional)
                double d = EOABC_COEFFICIENT;
                
                // Menampilkan konfigurasi algoritma ABC
                System.out.println("\n====== ABC ALGORITHM CONFIGURATION ======");
                System.out.println("- Swarm Size: " + populationSize);
                System.out.println("- Employed Bees: " + populationSize/2 + " (50% of swarm)");
                System.out.println("- Onlooker Bees: " + populationSize/2 + " (50% of swarm)");
                System.out.println("- Scout Bees: 1");
                System.out.println("- Dimensions: " + dimensions);
                System.out.println("- Limit: " + limit);
                System.out.println("- Max Iterations: " + Imax);
                System.out.println("- EOABC Enhancement: " + (USE_EOABC ? "Enabled (d=" + d + ")" : "Disabled"));
                System.out.println("========================================\n");
                
                // Inisialisasi algoritma ABC
                ABC abc = new ABC(Imax, populationSize, limit, d, USE_EOABC, cloudletList, vmlist, cloudletNumber);

                // Inisialisasi populasi
                System.out.println("Datacenter " + dataCenterIterator + " Population Initialization");
                Population population = abc.initPopulation(cloudletNumber, dataCenterIterator);

                // Menjalankan algoritma ABC
                abc.runABCAlgorithm(population, dataCenterIterator, cloudletIterator);

                // Mendapatkan solusi terbaik
                int[] bestSolution = abc.getBestVmAllocationForDatacenter(dataCenterIterator);
                double bestFitness = abc.getBestFitnessForDatacenter(dataCenterIterator);
                
                System.out.println("Best solution found for datacenter " + dataCenterIterator + 
                                  " with fitness " + bestFitness);

                // Menetapkan tugas ke VM berdasarkan solusi terbaik
                for (int assigner = 0 + (dataCenterIterator - 1) * 9 + cloudletIterator * 54;
                     assigner < 9 + (dataCenterIterator - 1) * 9 + cloudletIterator * 54; assigner++) {
                    int vmId = bestSolution[assigner - (dataCenterIterator - 1) * 9 - cloudletIterator * 54];
                    broker.bindCloudletToVm(assigner, vmId);
                }
                

            }
        }

        // Memulai simulasi dan mencetak hasil
        CloudSim.startSimulation();

        // Mendapatkan daftar cloudlet yang telah diterima
        List<Cloudlet> newList = broker.getCloudletReceivedList();

        // Menghentikan simulasi
        CloudSim.stopSimulation();

        // Mencetak daftar cloudlet dan statistik
        printCloudletList(newList, trialNum);

        Log.printLine("Cloud Simulation with ABC" + (USE_EOABC ? "+EOABC" : "") + 
                     " Trial " + trialNum + " using " + DATASET_TYPE + " dataset finished!");
    }

    /**
     * Membuat daftar cloudlet
     * 
     * Metode ini membaca data panjang tugas dari file dataset dan membuat cloudlet
     * dengan karakteristik yang sesuai.
     * 
     * @param userId ID pengguna yang akan memiliki cloudlet
     * @param cloudlets Jumlah cloudlet yang akan dibuat
     * @return Daftar cloudlet yang dibuat
     */
    private static List<Cloudlet> createCloudlet(int userId, int cloudlets) {
        // Mendapatkan nilai panjang tugas dari file dataset
        ArrayList<Double> randomSeed = getSeedValue(cloudlets);

        LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

        // Parameter cloudlet
        long fileSize = 300; // Ukuran file input (byte)
        long outputSize = 300; // Ukuran file output (byte)
        int pesNumber = 1; // Jumlah CPU core yang dibutuhkan
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // Membuat cloudlet dengan panjang tugas yang berbeda
        for (int i = 0; i < cloudlets; i++) {
            long length = 0;

            if (randomSeed.size() > i) {
                length = Double.valueOf(randomSeed.get(i)).longValue();
            }

            Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            list.add(cloudlet);
        }
        // Mengacak urutan cloudlet untuk simulasi yang lebih realistis
        Collections.shuffle(list);

        return list;
    }

    /**
     * Membuat daftar VM 
     * 
     * Metode ini membuat sejumlah VM dengan karakteristik yang berbeda.
     * VM dibuat dengan tiga jenis konfigurasi yang berbeda (RAM, MIPS).
     * 
     * @param userId ID pengguna yang akan memiliki VM
     * @param vms Jumlah VM yang akan dibuat
     * @return Daftar VM yang dibuat
     */
    private static List<Vm> createVM(int userId, int vms) {
        LinkedList<Vm> list = new LinkedList<Vm>();

        // Parameter VM
        long size = 10000; // Ukuran image (MB)
        int[] ram = { 512, 1024, 2048 }; // RAM (MB) untuk setiap jenis VM
        int[] mips = { 400, 500, 600 }; // Kecepatan processor (MIPS) untuk setiap jenis VM
        long bw = 1000; // Bandwidth (Mb/s)
        int pesNumber = 1; // Jumlah CPU core
        String vmm = "Xen"; // Virtual Machine Monitor

        Vm[] vm = new Vm[vms];

        // Membuat VM dengan karakteristik yang berbeda-beda
        for (int i = 0; i < vms; i++) {
            vm[i] = new Vm(i, userId, mips[i % 3], pesNumber, ram[i % 3], bw, size, vmm, new CloudletSchedulerSpaceShared());
            list.add(vm[i]);
        }

        return list;
    }

    /**
     * Mendapatkan nilai panjang tugas dari file dataset
     * 
     * Metode ini membaca nilai panjang tugas dari file dataset yang dipilih.
     * 
     * @param cloudletcount Jumlah cloudlet yang dibutuhkan
     * @return Daftar nilai panjang tugas
     */
    private static ArrayList<Double> getSeedValue(int cloudletcount) {
        ArrayList<Double> seed = new ArrayList<Double>();
        try {
            File fobj;
            
            // Memilih file dataset yang benar berdasarkan jenis dataset
            if (DATASET_TYPE.equals("SDSC")) {
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/SDSC/SDSC7395.txt");
            } else if (DATASET_TYPE.equals("RandSimple")) {
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/randomSimple/RandSimple"+DATASET_SIZE+".txt");
            } else if (DATASET_TYPE.equals("RandStratified")) {
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/randomStratified/RandStratified"+DATASET_SIZE+".txt");
            } else {
                throw new IllegalArgumentException("Invalid dataset type: " + DATASET_TYPE + 
                    ". Valid options are: RandSimple, RandStratified, SDSC");
            }
            
            Log.printLine("Loading dataset file: " + fobj.getPath());
            java.util.Scanner readFile = new java.util.Scanner(fobj);

            // Membaca nilai dari file dataset
            while (readFile.hasNextLine() && cloudletcount > 0) {
                seed.add(readFile.nextDouble());
                cloudletcount--;
            }
            readFile.close();

        } catch (FileNotFoundException e) {
            Log.printLine("ERROR: Dataset file not found. Please check the path and file name.");
            e.printStackTrace();
        }

        return seed;
    }

    /**
     * Membuat pusat data dengan karakteristik tertentu
     * 
     * Metode ini membuat pusat data dengan karakteristik yang ditentukan
     * termasuk daftar host, kebijakan alokasi VM, dan model daya.
     * 
     * @param name Nama pusat data
     * @param hostId ID awal untuk host di pusat data
     * @return PowerDatacenter Pusat data yang dibuat
     */
    private static PowerDatacenter createDatacenter(String name, int hostId) {
        // Daftar host di pusat data
        List<PowerHost> hostList = new ArrayList<PowerHost>();

        // Daftar Processing Elements (PE) untuk setiap host
        List<Pe> peList1 = new ArrayList<Pe>();
        List<Pe> peList2 = new ArrayList<Pe>();
        List<Pe> peList3 = new ArrayList<Pe>();

        // Kecepatan PE untuk setiap host
        // int mipsunused = 300; // MIPS yang tidak digunakan
        int mips1 = 400; // MIPS untuk host 1
        int mips2 = 500; // MIPS untuk host 2
        int mips3 = 600; // MIPS untuk host 3

        // Menambahkan PE ke daftar PE untuk setiap host
        peList1.add(new Pe(0, new PeProvisionerSimple(mips1))); 
        peList1.add(new Pe(1, new PeProvisionerSimple(mips1)));
        peList1.add(new Pe(2, new PeProvisionerSimple(mips1)));
        // peList1.add(new Pe(3, new PeProvisionerSimple(mipsunused)));
        peList2.add(new Pe(3, new PeProvisionerSimple(mips2)));
        peList2.add(new Pe(4, new PeProvisionerSimple(mips2)));
        peList2.add(new Pe(5, new PeProvisionerSimple(mips2)));
        // peList2.add(new Pe(7, new PeProvisionerSimple(mipsunused)));
        peList3.add(new Pe(6, new PeProvisionerSimple(mips3)));
        peList3.add(new Pe(7, new PeProvisionerSimple(mips3)));
        peList3.add(new Pe(8, new PeProvisionerSimple(mips3)));
        // peList3.add(new Pe(11, new PeProvisionerSimple(mipsunused)));

        // Parameter host
        int ram = 128000; // RAM (MB)
        long storage = 1000000; // Storage (MB)
        int bw = 10000; // Bandwidth (Mb/s)
        int maxpower = 117; // Konsumsi daya maksimum
        int staticPowerPercentage = 50; // Persentase daya statis

        // Membuat host dan menambahkannya ke daftar host
        hostList.add(
            new PowerHostUtilizationHistory(
                hostId, new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList1,
                new VmSchedulerTimeShared(peList1),
                new PowerModelLinear(maxpower, staticPowerPercentage)));
        hostId++;

        hostList.add(
            new PowerHostUtilizationHistory(
                hostId, new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList2,
                new VmSchedulerTimeShared(peList2),
                new PowerModelLinear(maxpower, staticPowerPercentage)));
        hostId++;

        hostList.add(
            new PowerHostUtilizationHistory(
                hostId, new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList3,
                new VmSchedulerTimeShared(peList3),
                new PowerModelLinear(maxpower, staticPowerPercentage)));

        // Karakteristik pusat data
        String arch = "x86"; // Arsitektur
        String os = "Linux"; // Sistem operasi
        String vmm = "Xen"; // Virtual Machine Monitor
        double time_zone = 10.0; // Zona waktu
        double cost = 3.0; // Biaya penggunaan per jam
        double costPerMem = 0.05; // Biaya per GB RAM
        double costPerStorage = 0.1; // Biaya per GB storage
        double costPerBw = 0.1; // Biaya per Gb/s bandwidth
        LinkedList<Storage> storageList = new LinkedList<Storage>();

        // Membuat karakteristik pusat data
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        // Membuat pusat data
        PowerDatacenter datacenter = null;
        try {
            datacenter = new PowerDatacenter(name, characteristics, new PowerVmAllocationPolicySimple(hostList), storageList, 9); 
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Membuat broker data center
     * 
     * Broker data center bertanggung jawab untuk mengelola VM dan cloudlet
     * untuk pengguna.
     * 
     * @return DatacenterBroker Broker yang dibuat
     */
    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    /**
     * Mencetak daftar cloudlet dan statistiknya
     * 
     * Metode ini mencetak informasi tentang cloudlet yang telah diproses
     * dan menghitung berbagai metrik kinerja.
     * 
     * @param list Daftar cloudlet yang telah diproses
     * @param trialNum Nomor percobaan saat ini
     */
    private static void printCloudletList(List<Cloudlet> list, int trialNum) throws FileNotFoundException {
        // Inisialisasi output yang dicetak ke nol
        int size = list.size();
        Cloudlet cloudlet = null;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT TRIAL " + trialNum + " ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
            "Data center ID" + indent + "VM ID" + indent + "Time"
            + indent + "Start Time" + indent + "Finish Time" + indent + "Waiting Time");

        // Variabel untuk menghitung statistik
        double waitTimeSum = 0.0;
        double CPUTimeSum = 0.0;
        int totalValues = 0;
        DecimalFormat dft = new DecimalFormat("###,##");

        double response_time[] = new double[size];

        // Mencetak status semua cloudlet
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(cloudlet.getCloudletId() + indent + indent);

            // Jika cloudlet berhasil diproses
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");
                CPUTimeSum = CPUTimeSum + cloudlet.getActualCPUTime();
                waitTimeSum = waitTimeSum + cloudlet.getWaitingTime();
                Log.printLine(
                    indent + indent + indent + (cloudlet.getResourceId() - 1) + indent + indent + indent + cloudlet.getVmId() +
                        indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent
                        + dft.format(cloudlet.getExecStartTime()) +
                        indent + indent + dft.format(cloudlet.getFinishTime()) + indent + indent + indent
                        + dft.format(cloudlet.getWaitingTime()));
                totalValues++;

                response_time[i] = cloudlet.getActualCPUTime();
            }
        }
        
        // Menghitung statistik untuk waktu respons
        DoubleSummaryStatistics stats = DoubleStream.of(response_time).summaryStatistics();

        // ================= VM STATISTICS =================
        // Tracking CPU time and task count per VM
        java.util.Map<Integer, Double> vmCpuTimeMap = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> vmTaskCountMap = new java.util.HashMap<>();
        
        // Tracking individual tasks per VM for detailed execution time display
        java.util.Map<Integer, java.util.List<String>> vmTaskDetailsMap = new java.util.HashMap<>();
        
        // Initialize all VMs with 0 values and empty task lists
        for (int i = 0; i < vmlist.size(); i++) {
            vmCpuTimeMap.put(i, 0.0);
            vmTaskCountMap.put(i, 0);
            vmTaskDetailsMap.put(i, new java.util.ArrayList<String>());
        }
        
        // Aggregate data per VM from successful cloudlets
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                int vmId = cloudlet.getVmId();
                int cloudletId = cloudlet.getCloudletId();
                double cpuTime = cloudlet.getActualCPUTime();
                
                // Update CPU time for this VM
                vmCpuTimeMap.put(vmId, vmCpuTimeMap.get(vmId) + cpuTime);
                
                // Update task count for this VM
                vmTaskCountMap.put(vmId, vmTaskCountMap.get(vmId) + 1);
                
                // Store individual task details (Task ID and execution time)
                // Format: "TaskID:ExecutionTime" for easier sorting later
                String taskDetail = String.format("%d:%.0f", cloudletId, cpuTime);
                vmTaskDetailsMap.get(vmId).add(taskDetail);
            }
        }
        
        // Print VM statistics
        Log.printLine();
        Log.printLine("================= VM STATISTICS =================");
        Log.printLine("VM CPU Time and Task Distribution with Individual Task Execution Times:");
        Log.printLine("Format: VM ID = Total CPU Time (ms), Task Count");
        Log.printLine("        Task ID: Individual Execution Time (ms)");
        Log.printLine("-------------------------------------------------------------------");
        
        for (int vmId = 0; vmId < vmlist.size(); vmId++) {
            double vmCpuTime = vmCpuTimeMap.get(vmId);
            int vmTaskCount = vmTaskCountMap.get(vmId);
            java.util.List<String> taskDetails = vmTaskDetailsMap.get(vmId);
            
            // Print VM summary
            Log.printLine(String.format("VM %d = %.0fms, %d tasks", 
                          vmId, vmCpuTime, vmTaskCount));
            
            // Print individual task execution times if there are tasks
            if (vmTaskCount > 0) {
                // Parse and sort tasks by execution time (descending)
                java.util.List<TaskInfo> taskInfoList = new java.util.ArrayList<>();
                for (String taskDetail : taskDetails) {
                    String[] parts = taskDetail.split(":");
                    int taskId = Integer.parseInt(parts[0]);
                    double execTime = Double.parseDouble(parts[1]);
                    taskInfoList.add(new TaskInfo(taskId, execTime));
                }
                
                // Sort by execution time (descending - longest first)
                java.util.Collections.sort(taskInfoList, new java.util.Comparator<TaskInfo>() {
                    public int compare(TaskInfo a, TaskInfo b) {
                        return Double.compare(b.execTime, a.execTime); // Descending order
                    }
                });
                
                // Print sorted tasks
                for (TaskInfo taskInfo : taskInfoList) {
                    Log.printLine(String.format("    Task %d: %.0fms", 
                                  taskInfo.taskId, taskInfo.execTime));
                }
                
                // Calculate and display VM-specific statistics
                double minExecTime = taskInfoList.get(taskInfoList.size() - 1).execTime;
                double maxExecTime = taskInfoList.get(0).execTime;
                double avgExecTime = vmCpuTime / vmTaskCount;
                double rangeExecTime = maxExecTime - minExecTime;
                
                Log.printLine(String.format("    VM %d Stats: Min=%.0fms, Max=%.0fms, Avg=%.0fms, Range=%.0fms", 
                              vmId, minExecTime, maxExecTime, avgExecTime, rangeExecTime));
                
                // Write VM statistics to CSV
                try {
                    // Create task details string for CSV (TaskID:ExecTime format)
                    StringBuilder taskDetailsBuilder = new StringBuilder();
                    for (int j = 0; j < taskInfoList.size(); j++) {
                        if (j > 0) taskDetailsBuilder.append("|");  // Use pipe separator instead of semicolon
                        taskDetailsBuilder.append(String.format("T%d:%.0f", 
                                                taskInfoList.get(j).taskId, taskInfoList.get(j).execTime));
                    }
                    
                    // Properly escape the task details for CSV by replacing quotes
                    String taskDetailsEscaped = taskDetailsBuilder.toString().replace("\"", "\"\"");
                    
                    vmCsvWriter.write(String.format("%d,%d,%.0f,%d,\"%s\",%.0f,%.0f,%.0f,%.0f\n",
                        trialNum, vmId, vmCpuTime, vmTaskCount, 
                        taskDetailsEscaped,
                        minExecTime, maxExecTime, avgExecTime, rangeExecTime));
                    
                    // Log sample of task details format for verification (only for first few VMs)
                    if (vmId < 3 && trialNum == 1) {
                        Log.printLine(String.format("    CSV Format Sample VM %d: \"%s\"", vmId, taskDetailsEscaped));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            } else {
                Log.printLine("    No tasks assigned");
                
                // Write empty VM record to CSV
                try {
                    vmCsvWriter.write(String.format("%d,%d,%.0f,%d,\"%s\",%.0f,%.0f,%.0f,%.0f\n",
                        trialNum, vmId, 0.0, 0, "NO_TASKS", 0.0, 0.0, 0.0, 0.0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            // Add a separator between VMs for better readability
            if (vmId < vmlist.size() - 1) {
                Log.printLine("    ---");
            }
        }
        
        // Calculate and display VM utilization statistics
        double totalVmCpuTime = 0.0;
        int totalTasks = 0;
        
        // Calculate totals using traditional loop
        for (int vmId = 0; vmId < vmlist.size(); vmId++) {
            totalVmCpuTime += vmCpuTimeMap.get(vmId);
            totalTasks += vmTaskCountMap.get(vmId);
        }
        
        Log.printLine("-------------------------------------------");
        Log.printLine("VM Summary:");
        Log.printLine("Total VMs: " + vmlist.size());
        Log.printLine("Total CPU Time across all VMs: " + String.format("%.0fms", totalVmCpuTime));
        Log.printLine("Total Tasks distributed: " + totalTasks);
        Log.printLine("Average CPU Time per VM: " + String.format("%.0fms", totalVmCpuTime / vmlist.size()));
        Log.printLine("Average Tasks per VM: " + String.format("%.1f", (double)totalTasks / vmlist.size()));
        
        // Find VM with highest and lowest utilization using traditional loop
        int maxTaskVmId = -1;
        int minTaskVmId = -1;
        int maxTasks = -1;
        int minTasks = Integer.MAX_VALUE;
        
        for (int vmId = 0; vmId < vmlist.size(); vmId++) {
            int currentTasks = vmTaskCountMap.get(vmId);
            if (currentTasks > maxTasks) {
                maxTasks = currentTasks;
                maxTaskVmId = vmId;
            }
            if (currentTasks < minTasks) {
                minTasks = currentTasks;
                minTaskVmId = vmId;
            }
        }
            
        if (maxTaskVmId != -1 && minTaskVmId != -1) {
            Log.printLine("Highest loaded VM: VM " + maxTaskVmId + " with " + vmTaskCountMap.get(maxTaskVmId) + " tasks");
            Log.printLine("Lowest loaded VM: VM " + minTaskVmId + " with " + vmTaskCountMap.get(minTaskVmId) + " tasks");
            
            // Calculate load balance ratio
            double loadImbalance = (double)vmTaskCountMap.get(maxTaskVmId) / vmTaskCountMap.get(minTaskVmId);
            Log.printLine("Load Imbalance Ratio: " + String.format("%.2f", loadImbalance) + " (Max/Min tasks)");
        }
        Log.printLine("================================================");
        
        // Flush VM CSV writer to ensure data is written
        try {
            vmCsvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Mencetak berbagai metrik kinerja dengan detail kalkulasi
        Log.printLine();
        Log.printLine("============= PERFORMANCE METRICS CALCULATIONS =============");
        
        // 1. Average Wait Time
        Log.printLine("\n1. AVERAGE WAIT TIME:");
        Log.printLine("   Total Wait Time: " + waitTimeSum);
        Log.printLine("   Number of Cloudlets: " + totalValues);
        double avgWaitTime = waitTimeSum / totalValues; // Fix: use totalValues for consistency
        Log.printLine("   Average Wait Time = Total Wait Time / Number of Cloudlets = " + 
                      String.format("%,.6f", waitTimeSum) + " / " + totalValues + " = " + 
                      String.format("%,.6f", avgWaitTime));
        
        // 2. Average Start Time
        Log.printLine("\n2. AVERAGE START TIME:");
        double totalStartTime = 0.0;
        for (int i = 0; i < size; i++) {
            if (list.get(i).getCloudletStatus() == Cloudlet.SUCCESS) { // Only count successful cloudlets
                totalStartTime += list.get(i).getExecStartTime();
            }
        }
        double avgStartTime = totalStartTime / totalValues; // Fix: use totalValues for consistency
        Log.printLine("   Total Start Time: " + String.format("%,.6f", totalStartTime));
        Log.printLine("   Number of Cloudlets: " + totalValues);
        Log.printLine("   Average Start Time = Total Start Time / Number of Cloudlets = " + 
                      String.format("%,.6f", totalStartTime) + " / " + totalValues + " = " + 
                      String.format("%,.6f", avgStartTime));
        
        // 3. Average Execution Time
        Log.printLine("\n3. AVERAGE EXECUTION TIME:");
        Log.printLine("   Total CPU Time: " + String.format("%,.6f", CPUTimeSum));
        Log.printLine("   Number of Cloudlets: " + totalValues);
        double avgExecTime = CPUTimeSum / totalValues;
        Log.printLine("   Average Execution Time = Total CPU Time / Number of Cloudlets = " + 
                      String.format("%,.6f", CPUTimeSum) + " / " + totalValues + " = " + 
                      String.format("%,.6f", avgExecTime));
        
        // 4. Average Finish Time
        Log.printLine("\n4. AVERAGE FINISH TIME:");
        double totalFinishTime = 0.0;
        for (int i = 0; i < size; i++) {
            if (list.get(i).getCloudletStatus() == Cloudlet.SUCCESS) { // Only count successful cloudlets
                totalFinishTime += list.get(i).getFinishTime();
            }
        }
        double avgFinishTime = totalFinishTime / totalValues; // Fix: use totalValues for consistency
        Log.printLine("   Total Finish Time: " + String.format("%,.6f", totalFinishTime));
        Log.printLine("   Number of Cloudlets: " + totalValues);
        Log.printLine("   Average Finish Time = Total Finish Time / Number of Cloudlets = " + 
                      String.format("%,.6f", totalFinishTime) + " / " + totalValues + " = " + 
                      String.format("%,.6f", avgFinishTime));
        
        // 5. Throughput
        Log.printLine("\n5. THROUGHPUT:");
        double maxFinishTime = 0.0;
        for (int i = 0; i < size; i++) {
            if (list.get(i).getCloudletStatus() == Cloudlet.SUCCESS) { // Only consider successful cloudlets
                if (list.get(i).getFinishTime() > maxFinishTime) {
                    maxFinishTime = list.get(i).getFinishTime();
                }
            }
        }
        double throughput = totalValues / maxFinishTime; // Fix: use totalValues for successful cloudlets
        Log.printLine("   Number of Cloudlets Completed: " + totalValues);
        Log.printLine("   Maximum Finish Time: " + String.format("%,.6f", maxFinishTime));
        Log.printLine("   Throughput = Number of Cloudlets / Maximum Finish Time = " + 
                      totalValues + " / " + String.format("%,.6f", maxFinishTime) + " = " + 
                      String.format("%,.6f", throughput));
        
        // 6. Makespan
        Log.printLine("\n6. MAKESPAN:");
        // Menggunakan nilai maksimum dari waktu penyelesaian sebagai makespan
        double makespan = maxFinishTime;
        Log.printLine("   Makespan = Maximum Finish Time of All Cloudlets = " + 
                      String.format("%,.6f", makespan));
        
        // 7. Imbalance Degree
        Log.printLine("\n7. IMBALANCE DEGREE:");
        double minResponseTime = stats.getMin();
        double maxResponseTime = stats.getMax();
        double avgResponseTime = avgExecTime;
        double imbalanceDegree = (maxResponseTime - minResponseTime) / avgResponseTime;
        Log.printLine("   Maximum Response Time: " + String.format("%,.6f", maxResponseTime));
        Log.printLine("   Minimum Response Time: " + String.format("%,.6f", minResponseTime));
        Log.printLine("   Average Response Time: " + String.format("%,.6f", avgResponseTime));
        Log.printLine("   Imbalance Degree = (Max Response Time - Min Response Time) / Avg Response Time = " + 
                      "(" + String.format("%,.6f", maxResponseTime) + " - " + 
                      String.format("%,.6f", minResponseTime) + ") / " + 
                      String.format("%,.6f", avgResponseTime) + " = " + 
                      String.format("%,.6f", imbalanceDegree));
        
        // 8. Total Scheduling Length
        Log.printLine("\n8. TOTAL SCHEDULING LENGTH:");
        double schedulingLength = waitTimeSum + makespan;
        Log.printLine("   Total Wait Time: " + String.format("%,.6f", waitTimeSum));
        Log.printLine("   Makespan: " + String.format("%,.6f", makespan));
        Log.printLine("   Total Scheduling Length = Total Wait Time + Makespan = " + 
                      String.format("%,.6f", waitTimeSum) + " + " + 
                      String.format("%,.6f", makespan) + " = " + 
                      String.format("%,.6f", schedulingLength));
        
        // 9. Resource Utilization
        Log.printLine("\n9. RESOURCE UTILIZATION:");
        int totalVMs = vmlist.size();
        double resourceUtilization = (CPUTimeSum / (makespan * totalVMs)) * 100;
        Log.printLine("   Total CPU Time: " + String.format("%,.6f", CPUTimeSum));
        Log.printLine("   Makespan: " + String.format("%,.6f", makespan));
        Log.printLine("   Total VMs: " + totalVMs);
        Log.printLine("   Resource Utilization = (Total CPU Time / (Makespan * Total VMs)) * 100 = " + 
                      "(" + String.format("%,.6f", CPUTimeSum) + " / (" + 
                      String.format("%,.6f", makespan) + " * " + totalVMs + ")) * 100 = " + 
                      String.format("%,.6f", resourceUtilization) + "%");
        
        // 10. Energy Consumption
        Log.printLine("\n10. ENERGY CONSUMPTION:");
        double dc1Power = datacenter1.getPower();
        double dc2Power = datacenter2.getPower();
        double dc3Power = datacenter3.getPower();
        double dc4Power = datacenter4.getPower();
        double dc5Power = datacenter5.getPower();
        double dc6Power = datacenter6.getPower();
        double totalPower = dc1Power + dc2Power + dc3Power + dc4Power + dc5Power + dc6Power;
        double energyConsumption = totalPower / (3600 * 1000); // Convert to kWh
        
        Log.printLine("   Datacenter 1 Power: " + String.format("%,.6f", dc1Power) + " Watts");
        Log.printLine("   Datacenter 2 Power: " + String.format("%,.6f", dc2Power) + " Watts");
        Log.printLine("   Datacenter 3 Power: " + String.format("%,.6f", dc3Power) + " Watts");
        Log.printLine("   Datacenter 4 Power: " + String.format("%,.6f", dc4Power) + " Watts");
        Log.printLine("   Datacenter 5 Power: " + String.format("%,.6f", dc5Power) + " Watts");
        Log.printLine("   Datacenter 6 Power: " + String.format("%,.6f", dc6Power) + " Watts");
        Log.printLine("   Total Power: " + String.format("%,.6f", totalPower) + " Watts");
        Log.printLine("   Energy Consumption = Total Power / (3600 * 1000) = " + 
                      String.format("%,.6f", totalPower) + " / " + (3600 * 1000) + " = " + 
                      String.format("%,.6f", energyConsumption) + " kWh");
        
        Log.printLine("\n============= SUMMARY OF PERFORMANCE METRICS =============");
        Log.printLine("1. Average Wait Time: " + String.format("%,.6f", avgWaitTime));
        Log.printLine("2. Average Start Time: " + String.format("%,.6f", avgStartTime));
        Log.printLine("3. Average Execution Time: " + String.format("%,.6f", avgExecTime));
        Log.printLine("4. Average Finish Time: " + String.format("%,.6f", avgFinishTime));
        Log.printLine("5. Throughput: " + String.format("%,.6f", throughput));
        Log.printLine("6. Makespan: " + String.format("%,.6f", makespan));
        Log.printLine("7. Imbalance Degree: " + String.format("%,.6f", imbalanceDegree));
        Log.printLine("8. Total Scheduling Length: " + String.format("%,.6f", schedulingLength));
        Log.printLine("9. Resource Utilization: " + String.format("%,.6f", resourceUtilization) + "%");
        Log.printLine("10. Energy Consumption: " + String.format("%,.6f", energyConsumption) + " kWh");
        
        // Menulis hasil ke file CSV dengan format yang sama persis dengan console output
        try {
            csvWriter.write(String.format("%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                trialNum,
                avgWaitTime,
                avgStartTime,
                avgExecTime,
                avgFinishTime,
                throughput,
                makespan,
                imbalanceDegree,
                schedulingLength,
                resourceUtilization,
                energyConsumption));
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}