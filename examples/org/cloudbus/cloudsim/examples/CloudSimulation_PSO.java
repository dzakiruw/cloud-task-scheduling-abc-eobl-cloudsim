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


public class CloudSimulation_PSO {
    private static PowerDatacenter datacenter1, datacenter2, datacenter3, datacenter4, datacenter5, datacenter6;
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    private static BufferedWriter csvWriter;
    private static String resultFileName;
    
    // Configuration parameters
    private static String DATASET_TYPE = "RandStratified"; // RandSimple, RandStratified, or SDSC
    private static int DATASET_SIZE = 6; // For RandSimple and RandStratified (multiplied by 1000)
    private static final int NUM_TRIALS = 10; // Number of trials to run

    public static void main(String[] args) {
        Locale.setDefault(new Locale("en", "US"));
        
        // Process command line arguments if provided
        if (args.length >= 1) {
            String datasetArg = args[0].trim();
            if (datasetArg.equals("RandSimple") || datasetArg.equals("RandStratified") || datasetArg.equals("SDSC")) {
                DATASET_TYPE = datasetArg;
                Log.printLine("Dataset type set via command line: " + DATASET_TYPE);
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
        
        Log.printLine("Starting Cloud Simulation with PSO using " + DATASET_TYPE + " dataset...");

        try {
            // Setup CSV file for results
            resultFileName = "pso_" + DATASET_TYPE + "_" + (DATASET_TYPE.equals("SDSC") ? "7395" : DATASET_SIZE + "000") + "_results.csv";
            csvWriter = new BufferedWriter(new FileWriter(resultFileName));
            
            // Write CSV header
            csvWriter.write("Trial,Average Wait Time,Average Start Time,Average Execution Time,Average Finish Time," +
                          "Throughput,Makespan,Imbalance Degree,Total Scheduling Length,Resource Utilization,Energy Consumption\n");
            
            // Run multiple trials
            for (int trial = 1; trial <= NUM_TRIALS; trial++) {
                Log.printLine("\n\n========== TRIAL " + trial + " OF " + NUM_TRIALS + " ==========\n");
                runSimulation(trial);
            }
            
            csvWriter.close();
            Log.printLine("\nAll trials completed. Results written to " + resultFileName);
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Simulation terminated due to an error");
            try {
                if (csvWriter != null) {
                    csvWriter.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    private static void runSimulation(int trialNum) throws Exception {
        System.out.println("==================================================");
        System.out.println("Starting simulation with " + DATASET_TYPE + " dataset");
        System.out.println("1 dataset × 1 trial = 1 total trial");
        System.out.println("==================================================");
        
        System.out.println("\nProcessing " + DATASET_TYPE + " dataset...");
        Log.printLine("Running " + DATASET_TYPE + " dataset...");
        
        int num_user = 1;
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;

        CloudSim.init(num_user, calendar, trace_flag);

        int hostId = 0;

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

        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();
        int vmNumber = 54;
        int cloudletNumber = DATASET_TYPE.equals("SDSC") ? 7395 : DATASET_SIZE * 1000;

        System.out.println("      └─ Creating VMs and Cloudlets...");
        vmlist = createVM(brokerId, vmNumber);
        cloudletList = createCloudlet(brokerId, cloudletNumber);

        broker.submitVmList(vmlist);
        broker.submitCloudletList(cloudletList);

        int cloudletLoopingNumber = cloudletNumber / vmNumber - 1;

        System.out.println("      └─ Running PSO algorithm for task scheduling...");
        for (int cloudletIterator = 0; cloudletIterator <= cloudletLoopingNumber; cloudletIterator++) {
            System.out.println("        └─ Cloudlet Iteration " + cloudletIterator + "/" + cloudletLoopingNumber);

            for (int dataCenterIterator = 1; dataCenterIterator <= 6; dataCenterIterator++) {
                System.out.println("          └─ Processing Datacenter " + dataCenterIterator + "/6");
                
                // Parameters for PSO
                int Imax = 5;
                int populationSize = 30;
                double w = 0.6; // Static inertia weight
                double l1 = 1.5;
                double l2 = 2.5;

                PSO PSO = new PSO(Imax, populationSize, w, l1, l2, cloudletList, vmlist, cloudletNumber);
  
                System.out.println("            └─ Initializing population");
                PopulationPSO population = PSO.initPopulation(cloudletNumber, dataCenterIterator);
  
                System.out.println("            └─ Running PSO Algorithm");
                PSO.evaluateFitness(population, dataCenterIterator, cloudletIterator);
  
                int iteration = 1;
                while (iteration <= Imax) {
                    PSO.updateVelocitiesAndPositions(population, iteration, dataCenterIterator);
                    PSO.evaluateFitness(population, dataCenterIterator, cloudletIterator);
                    iteration++;
                }
  
                int[] bestSolution = PSO.getBestVmAllocationForDatacenter(dataCenterIterator);
  
                System.out.println("            └─ Assigning tasks to VMs");
                for (int assigner = 0 + (dataCenterIterator - 1) * 9 + cloudletIterator * 54;
                     assigner < 9 + (dataCenterIterator - 1) * 9 + cloudletIterator * 54; assigner++) {
                    int vmId = bestSolution[assigner - (dataCenterIterator - 1) * 9 - cloudletIterator * 54];
                    broker.bindCloudletToVm(assigner, vmId);
                }
            }
        }
  
        System.out.println("      └─ Starting CloudSim simulation...");
        CloudSim.startSimulation();
  
        List<Cloudlet> newList = broker.getCloudletReceivedList();
  
        CloudSim.stopSimulation();
        System.out.println("      └─ Simulation completed, calculating results...");

        printCloudletList(newList, trialNum);
        
        System.out.println("\n==================================================");
        System.out.println("All 1 trial completed successfully!");
        System.out.println("Results saved to: " + resultFileName);
        System.out.println("==================================================");
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

        // Mencetak berbagai metrik kinerja dengan detail kalkulasi
        Log.printLine();
        Log.printLine("============= PERFORMANCE METRICS CALCULATIONS =============");
        
        // 1. Average Wait Time
        Log.printLine("\n1. AVERAGE WAIT TIME:");
        Log.printLine("   Total Wait Time: " + waitTimeSum);
        Log.printLine("   Number of Cloudlets: " + totalValues);
        double avgWaitTime = waitTimeSum / size;
        Log.printLine("   Average Wait Time = Total Wait Time / Number of Cloudlets = " + 
                      String.format("%,.6f", waitTimeSum) + " / " + size + " = " + 
                      String.format("%,.6f", avgWaitTime));
        
        // 2. Average Start Time
        Log.printLine("\n2. AVERAGE START TIME:");
        double totalStartTime = 0.0;
        for (int i = 0; i < size; i++) {
            totalStartTime += list.get(i).getExecStartTime();
        }
        double avgStartTime = totalStartTime / size;
        Log.printLine("   Total Start Time: " + String.format("%,.6f", totalStartTime));
        Log.printLine("   Number of Cloudlets: " + size);
        Log.printLine("   Average Start Time = Total Start Time / Number of Cloudlets = " + 
                      String.format("%,.6f", totalStartTime) + " / " + size + " = " + 
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
            totalFinishTime += list.get(i).getFinishTime();
        }
        double avgFinishTime = totalFinishTime / size;
        Log.printLine("   Total Finish Time: " + String.format("%,.6f", totalFinishTime));
        Log.printLine("   Number of Cloudlets: " + size);
        Log.printLine("   Average Finish Time = Total Finish Time / Number of Cloudlets = " + 
                      String.format("%,.6f", totalFinishTime) + " / " + size + " = " + 
                      String.format("%,.6f", avgFinishTime));
        
        // 5. Throughput
        Log.printLine("\n5. THROUGHPUT:");
        double maxFinishTime = stats.getMax();
        for (int i = 0; i < size; i++) {
            if (list.get(i).getFinishTime() > maxFinishTime) {
                maxFinishTime = list.get(i).getFinishTime();
            }
        }
        double throughput = size / maxFinishTime;
        Log.printLine("   Number of Cloudlets Completed: " + size);
        Log.printLine("   Maximum Finish Time: " + String.format("%,.6f", maxFinishTime));
        Log.printLine("   Throughput = Number of Cloudlets / Maximum Finish Time = " + 
                      size + " / " + String.format("%,.6f", maxFinishTime) + " = " + 
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
        
        // Menulis hasil ke file CSV
        try {
            csvWriter.write(String.format("%d,%f,%f,%f,%f,%f,%f,%f,%f,%f,%f\n",
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
        ArrayList<Double> randomSeed = getSeedValue(cloudlets);

        LinkedList<Cloudlet> list = new LinkedList<Cloudlet>();

        long fileSize = 300;
        long outputSize = 300;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        for (int i = 0; i < cloudlets; i++) {
            long length = 0;

            if (randomSeed.size() > i) {
                length = Double.valueOf(randomSeed.get(i)).longValue();
            }

            Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, 
                utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            list.add(cloudlet);
        }
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

        long size = 10000;
        int[] ram = { 512, 1024, 2048 }; 
        int[] mips = { 400, 500, 600 }; 
        long bw = 1000; 
        int pesNumber = 1; 
        String vmm = "Xen"; 

        Vm[] vm = new Vm[vms];

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
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/randomSimple/RandSimple"+DATASET_SIZE+"000.txt");
            } else if (DATASET_TYPE.equals("RandStratified")) {
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/randomStratified/RandStratified"+DATASET_SIZE+"000.txt");
            } else {
                Log.printLine("Invalid dataset type: " + DATASET_TYPE + ". Defaulting to RandSimple.");
                fobj = new File(System.getProperty("user.dir") + "/cloudsim-3.0.3/datasets/randomSimple/RandSimple"+DATASET_SIZE+"000.txt");
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
        List<PowerHost> hostList = new ArrayList<PowerHost>();

        List<Pe> peList1 = new ArrayList<Pe>();
        List<Pe> peList2 = new ArrayList<Pe>();
        List<Pe> peList3 = new ArrayList<Pe>();

        int mipsunused = 300; 
        int mips1 = 400; 
        int mips2 = 500;
        int mips3 = 600;

        peList1.add(new Pe(0, new PeProvisionerSimple(mips1))); 
        peList1.add(new Pe(1, new PeProvisionerSimple(mips1)));
        peList1.add(new Pe(2, new PeProvisionerSimple(mips1)));
        peList1.add(new Pe(3, new PeProvisionerSimple(mipsunused)));
        peList2.add(new Pe(4, new PeProvisionerSimple(mips2)));
        peList2.add(new Pe(5, new PeProvisionerSimple(mips2)));
        peList2.add(new Pe(6, new PeProvisionerSimple(mips2)));
        peList2.add(new Pe(7, new PeProvisionerSimple(mipsunused)));
        peList3.add(new Pe(8, new PeProvisionerSimple(mips3)));
        peList3.add(new Pe(9, new PeProvisionerSimple(mips3)));
        peList3.add(new Pe(10, new PeProvisionerSimple(mips3)));
        peList3.add(new Pe(11, new PeProvisionerSimple(mipsunused)));

        int ram = 128000;
        long storage = 1000000;
        int bw = 10000;
        int maxpower = 117; 
        int staticPowerPercentage = 50; 

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

        String arch = "x86"; 
        String os = "Linux"; 
        String vmm = "Xen"; 
        double time_zone = 10.0; 
        double cost = 3.0; 
        double costPerMem = 0.05; 
        double costPerStorage = 0.1; 
        double costPerBw = 0.1; 
        LinkedList<Storage> storageList = new LinkedList<Storage>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

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
}