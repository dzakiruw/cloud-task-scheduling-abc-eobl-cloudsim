package org.cloudbus.cloudsim.examples;

import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

/**
 * Implementasi Algoritma Artificial Bee Colony (ABC) untuk Penjadwalan Tugas di Cloud
 * 
 * Implementasi ini mengikuti pseudocode dan diagram alir yang disediakan:
 * 
 * Diagram Alir Algoritma ABC:
 * 1. Inisialisasi
 * 2. Fase Lebah Pekerja (Employed Bee)
 * 3. Fase Lebah Pengamat (Onlooker Bee)
 * 4. Simpan Solusi Sumber Makanan Terbaik
 * 5. Periksa Lebah Penjelajah (Scout Bee)
 * 6. Jika ada Lebah Penjelajah, lakukan Fase Lebah Penjelajah
 * 7. Periksa Kriteria Terminasi
 * 8. Jika belum terpenuhi, kembali ke Langkah 2
 * 9. Output Solusi Terbaik Akhir
 *
 * Pseudocode:
 * 1: begin
 * 2: Set iteration t = 1.
 * 3: Define problem dimension.
 * 4: Generate initial population.
 * 5: Evaluate the fitness of the individuals.
 * 6: while (termination condition not reached) do
 * 7:   for each employee bees:
 * 8:     Find a new food sources and evaluate the fitness.
 * 9:     Apply greedy selection mechanism.
 * 10:  end for
 * 11:  Calculate the probability for each food source.
 * 12:  for each onlooker bees:
 * 13:    Chooses a food source
 * 14:    Produce new food source
 * 15:    Evaluate the fitness.
 * 16:    Apply greedy selection mechanism.
 * 17:  end for
 * 18:  Scout Bee phase
 * 19:  if any employed bee becomes scout bee
 * 20:    Send the scout bee at a randomly produced food source.
 * 21:  end if
 * 22:  Set iteration t = t + 1.
 * 23: end while
 * 24: end
 *
 * Selain itu, implementasi ini juga menyertakan Elite Opposition-Based Learning (EOBL)
 * sebagai peningkatan opsional untuk meningkatkan kemampuan eksplorasi.
 */
public class ABC {
    // Parameter
    private int Imax; // Jumlah maksimum iterasi
    private int populationSize; // Ukuran populasi (ukuran koloni lebah)
    private int employedBeeCount; // Jumlah lebah pekerja (50% dari populasi)
    private int onlookerBeeCount; // Jumlah lebah pengamat (50% dari populasi)
    private int scoutBeeCount; // Jumlah lebah penjelajah (tepat 1)
    private double limit; // Batas untuk fase lebah penjelajah
    private int[] abandonmentCounter; // Pelacak sumber makanan yang ditinggalkan
    private double d; // Koefisien EOABC
    private boolean useEOABC; // Flag untuk mengaktifkan/menonaktifkan EOABC

    private List<Cloudlet> cloudletList; // Daftar cloudlet (tugas)
    private List<Vm> vmList; // Daftar VM (mesin virtual)

    private int numberOfDataCenters = 6; // Jumlah pusat data
    private double[] globalBestFitnesses; // Menyimpan nilai fitness terbaik untuk setiap pusat data
    private int[][] globalBestPositions; // Menyimpan posisi terbaik untuk setiap pusat data

    /**
     * Konstruktor kelas ABC
     * 
     * @param Imax Jumlah maksimum iterasi
     * @param populationSize Ukuran populasi (koloni lebah)
     * @param limit Batas percobaan untuk meninggalkan sumber makanan
     * @param d Koefisien untuk algoritma EOABC
     * @param useEOABC Flag penggunaan EOABC
     * @param cloudletList Daftar cloudlet yang akan dijadwalkan
     * @param vmList Daftar VM yang tersedia
     * @param chromosomeLength Panjang kromosom (jumlah dimensi masalah)
     */
    public ABC(int Imax, int populationSize, double limit, double d, boolean useEOABC,
               List<Cloudlet> cloudletList, List<Vm> vmList, int chromosomeLength) {
        this.Imax = Imax;
        this.populationSize = populationSize;
        this.limit = limit;
        this.d = d;
        this.useEOABC = useEOABC;
        this.cloudletList = cloudletList;
        this.vmList = vmList;
        
        // Definisikan jumlah lebah sesuai dengan persyaratan
        this.employedBeeCount = populationSize / 2; // 50% lebah pekerja
        this.onlookerBeeCount = populationSize / 2; // 50% lebah pengamat
        this.scoutBeeCount = 1; // Selalu 1 lebah penjelajah
        
        // Inisialisasi penghitung ditinggalkan untuk setiap sumber makanan
        this.abandonmentCounter = new int[populationSize];
        
        // Inisialisasi penyimpanan solusi terbaik untuk setiap pusat data
        globalBestFitnesses = new double[numberOfDataCenters];
        globalBestPositions = new int[numberOfDataCenters][];

        // Inisialisasi nilai fitness terbaik dan posisi
        for (int i = 0; i < numberOfDataCenters; i++) {
            globalBestFitnesses[i] = Double.NEGATIVE_INFINITY;
            globalBestPositions[i] = null;
        }
    }

    /**
     * Menginisialisasi populasi solusi/individu (sumber makanan)
     * Bersesuaian dengan langkah 4 pada pseudocode: "Generate initial population"
     * 
     * @param chromosomeLength Panjang kromosom setiap individu
     * @param dataCenterIterator Indeks pusat data saat ini
     * @return Populasi yang sudah diinisialisasi
     */
    public Population initPopulation(int chromosomeLength, int dataCenterIterator) {
        Population population = new Population(this.populationSize, chromosomeLength, dataCenterIterator);
        return population;
    }

    /**
     * Mengevaluasi nilai fitness setiap individu dalam populasi
     * Bersesuaian dengan langkah 5 pada pseudocode: "Evaluate the fitness of the individuals"
     * 
     * @param population Populasi yang akan dievaluasi
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    public void evaluateFitness(Population population, int dataCenterIterator, int cloudletIteration) {
        for (Individual individual : population.getIndividuals()) {
            // Hitung nilai fitness untuk setiap individu
            double fitness = calcFitness(individual, dataCenterIterator, cloudletIteration);
            individual.setFitness(fitness);

            // Perbarui solusi terbaik global jika ditemukan solusi yang lebih baik
            int dcIndex = dataCenterIterator - 1;
            if (fitness > globalBestFitnesses[dcIndex]) {
                globalBestFitnesses[dcIndex] = fitness;
                globalBestPositions[dcIndex] = individual.getChromosome().clone();
            }
        }
    }

    /**
     * Titik masuk utama algoritma - memanggil EOABC atau ABC standar berdasarkan konfigurasi
     * 
     * @param population Populasi yang akan dioptimalkan
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    public void runABCAlgorithm(Population population, int dataCenterIterator, int cloudletIteration) {
        if (useEOABC) {
            System.out.println("Running ABC algorithm with EOABC enhancement");
            runABCEOBL(population, dataCenterIterator, cloudletIteration);
        } else {
            System.out.println("Running standard ABC algorithm (without EOABC)");
            runABC(population, dataCenterIterator, cloudletIteration);
        }
    }
    
    /**
     * Implementasi algoritma EOABC berdasarkan pseudocode yang diberikan
     * 
     * @param population Populasi yang akan dioptimalkan
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    private void runABCEOBL(Population population, int dataCenterIterator, int cloudletIteration) {
        // t = 0; (Inisialisasi counter iterasi)
        int t = 0;
        System.out.println("Starting EOABC algorithm");
        System.out.println("Iteration counter initialized: " + t);
        
        // FEs = 0; (Inisialisasi counter evaluasi fungsi)
        int FEs = 0;
        System.out.println("Function evaluation counter initialized: " + FEs);
        
        // Initialize the population; (Inisialisasi populasi - sudah dilakukan melalui metode initPopulation)
        System.out.println("Initial population size: " + population.size());
        
        // Evaluasi populasi awal
        evaluateFitness(population, dataCenterIterator, cloudletIteration);
        FEs += population.size(); // Menambah counter evaluasi fungsi
        System.out.println("Initial fitness evaluation completed, FEs = " + FEs);
        
        // Reset penghitung ditinggalkan untuk fase lebah penjelajah
        for (int i = 0; i < abandonmentCounter.length; i++) {
            abandonmentCounter[i] = 0;
        }
        
        // Evaluasi fungsi maksimum (berdasarkan iterasi maksimum dan ukuran populasi)
        int MAX_FEs = Imax * populationSize;
        
        // while FEs < MAX_FEs do (Loop utama algoritma)
        while (FEs < MAX_FEs) {
            System.out.println("\n========== ITERATION " + (t+1) + " ==========");
            System.out.println("Function evaluations: " + FEs + "/" + MAX_FEs);
            
            // Pr = rand(0, 1); (Bangkitkan probabilitas acak)
            Random random = new Random();
            double Pr = random.nextDouble();
            System.out.println("Random probability Pr = " + Pr);
            
            // if Pr < Pe then (Kondisi untuk memilih antara EOABC atau ABC tradisional)
            double Pe = 0.5; // Ambang batas probabilitas
            if (Pr < Pe) {
                // Choose EN elite solutions from the current population; (Pilih solusi elit)
                int EN = Math.max(2, populationSize / 10); // Ukuran elit (misalnya, 10% teratas)
                Individual[] eliteSolutions = selectEliteSolutions(population, EN);
                System.out.println("Selected " + EN + " elite solutions as guides for opposition");
                
                // Calculate the lower and upper boundaries of the chosen elite solutions;
                // (Hitung batas bawah dan atas dari solusi elit yang dipilih)
                int minPosition = (dataCenterIterator - 1) * 9;
                int maxPosition = ((dataCenterIterator) * 9) - 1;
                System.out.println("Search space boundaries: [" + minPosition + ", " + maxPosition + "]");
                
                // EOP = {}; (Inisialisasi populasi oposisi kosong)
                List<Individual> oppositionPopulation = new ArrayList<>();
                
                // for i = 1 to SN do (Loop untuk setiap solusi dalam populasi)
                System.out.println("Generating elite opposition-based solutions for each individual...");
                for (int i = 0; i < populationSize; i++) {
                    // k = rand(0, 1); (Bangkitkan nilai acak untuk pemilihan elit)
                    double k = random.nextDouble();
                    
                    // Create the elite opposition-based solution EOi for the ith solution Xi;
                    // (Buat solusi berbasis oposisi elit untuk solusi ke-i)
                    Individual original = population.getIndividual(i);
                    Individual oppositeIndividual = createEliteOpposition(
                        original, eliteSolutions, minPosition, maxPosition, k);
                    
                    // Evaluate solution EOi; (Evaluasi solusi oposisi)
                    double oppositeFitness = calcFitness(oppositeIndividual, dataCenterIterator, cloudletIteration);
                    oppositeIndividual.setFitness(oppositeFitness);
                    
                    // EOP = EOP ∪ {EOi}; (Tambahkan solusi oposisi ke populasi oposisi)
                    oppositionPopulation.add(oppositeIndividual);
                    
                    // FEs = FEs + 1; (Perbarui counter evaluasi fungsi)
                    FEs++;
                }
                // end for
                
                // Choose the top best SN solutions from {P, EOP} for the next generation population;
                // (Pilih solusi terbaik dari gabungan populasi saat ini dan populasi oposisi)
                System.out.println("Merging populations and selecting best individuals...");
                mergeAndSelectBest(population, oppositionPopulation);
            } else {
                // else Execute the computation procedure of the traditional ABC;
                // (Jika Pr >= Pe, jalankan ABC tradisional)
                System.out.println("Using traditional ABC procedure - Pr >= Pe");
                
                // Fase Lebah Pekerja
                System.out.println("PHASE 1: Employed Bee Phase");
                employedBeePhase(population, dataCenterIterator, cloudletIteration);
                FEs += employedBeeCount;
                
                // Hitung probabilitas untuk fase lebah pengamat
                System.out.println("Calculating selection probabilities based on fitness");
                double[] probabilities = calculateProbabilities(population);
                
                // Fase Lebah Pengamat
                System.out.println("PHASE 2: Onlooker Bee Phase");
                onlookerBeePhase(population, probabilities, dataCenterIterator, cloudletIteration);
                FEs += onlookerBeeCount;
                
                // Simpan Sumber Makanan Terbaik
                storeBestFoodSource(population, dataCenterIterator);
                
                // Fase Lebah Penjelajah
                System.out.println("PHASE 3: Scout Bee Phase");
                boolean scoutBeeExists = checkForScoutBees(population);
                
                if (scoutBeeExists) {
                    System.out.println("Scout bee found - performing scout bee step");
                    scoutBeePhase(population, dataCenterIterator, cloudletIteration);
                    FEs += scoutBeeCount;
                } else {
                    System.out.println("No food sources abandoned - no scout bee needed");
                }
            }
            
            // Output solusi terbaik saat ini
            int dcIndex = dataCenterIterator - 1;
            System.out.println("Current Best Fitness: " + globalBestFitnesses[dcIndex]);
            
            // t = t + 1; (Perbarui counter iterasi)
            t++;
        }
        // end while
        
        System.out.println("\nEOABC ALGORITHM COMPLETED:");
        System.out.println("Total iterations: " + t);
        System.out.println("Total function evaluations: " + FEs);
        System.out.println("Best fitness: " + globalBestFitnesses[dataCenterIterator - 1]);
    }
    
    /**
     * Implementasi algoritma ABC standar
     * 
     * @param population Populasi yang akan dioptimasi
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    private void runABC(Population population, int dataCenterIterator, int cloudletIteration) {
        // 1: begin (Mulai algoritma)
        
        // 2: Set iteration t = 1 (Inisialisasi penghitung iterasi)
        int t = 1;
        System.out.println("Starting standard ABC algorithm");
        System.out.println("Iteration counter initialized: " + t);
        
        // 3: Define problem dimension (Definisikan dimensi masalah)
        int dimensions = population.getIndividual(0).getChromosomeLength();
        System.out.println("Problem dimension defined: " + dimensions);
        
        // 4: Generate initial population (Sudah dilakukan melalui metode initPopulation)
        System.out.println("Initial population size: " + population.size());
        
        // 5: Evaluate the fitness of the individuals (Evaluasi fitness)
        evaluateFitness(population, dataCenterIterator, cloudletIteration);
        System.out.println("Initial fitness evaluation completed");
        
        // Reset penghitung ditinggalkan untuk fase lebah penjelajah
        for (int i = 0; i < abandonmentCounter.length; i++) {
            abandonmentCounter[i] = 0;
        }
        
        // 6: while (termination condition not reached) do (Loop utama algoritma)
        while (t < Imax) {
            System.out.println("\n========== ITERATION " + (t+1) + " ==========");
            
            // 7: for each employee bees: (Fase Lebah Pekerja)
            System.out.println("PHASE 1: Employed Bee Phase");
            employedBeePhase(population, dataCenterIterator, cloudletIteration);
            
            // 11: Calculate the probability for each food source (Hitung probabilitas)
            System.out.println("Calculating selection probabilities based on fitness");
            double[] probabilities = calculateProbabilities(population);
            
            // 12: for each onlooker bees: (Fase Lebah Pengamat)
            System.out.println("PHASE 2: Onlooker Bee Phase");
            onlookerBeePhase(population, probabilities, dataCenterIterator, cloudletIteration);
            
            // Store Best Food Source Solution (Simpan solusi terbaik - dari diagram alir)
            storeBestFoodSource(population, dataCenterIterator);
            
            // 18: Scout Bee phase (Fase Lebah Penjelajah)
            System.out.println("PHASE 3: Scout Bee Phase");
            
            // 19: if any employed bee becomes scout bee (Cek lebah penjelajah)
            boolean scoutBeeExists = checkForScoutBees(population);
            
            // 20: Send the scout bee at a randomly produced food source (Kirim lebah penjelajah)
            if (scoutBeeExists) {
                System.out.println("Scout bee found - performing scout bee step");
                scoutBeePhase(population, dataCenterIterator, cloudletIteration);
            } else {
                System.out.println("No food sources abandoned - no scout bee needed");
            }
            
            // Output solusi terbaik saat ini
            int dcIndex = dataCenterIterator - 1;
            System.out.println("Current Best Fitness: " + globalBestFitnesses[dcIndex]);
            
            // 22: Set iteration t = t + 1 (Perbarui counter iterasi)
            t++;
        }
        // 23-24: end while, end (Akhiri algoritma)
        
        System.out.println("\nABC ALGORITHM COMPLETED:");
        System.out.println("Total iterations: " + t);
        System.out.println("Best fitness: " + globalBestFitnesses[dataCenterIterator - 1]);
    }
    
    /**
     * Memeriksa apakah ada sumber makanan yang telah mencapai batas ditinggalkan
     * Jika ya, maka ada lebah penjelajah (scout bee) yang akan mencari sumber makanan baru
     * Bersesuaian dengan langkah 19 pada pseudocode: "if any employed bee becomes scout bee"
     * 
     * @param population Populasi saat ini
     * @return true jika ada lebah penjelajah, false jika tidak ada
     */
    private boolean checkForScoutBees(Population population) {
        for (int i = 0; i < employedBeeCount; i++) {
            if (abandonmentCounter[i] > limit) {
                return true; // Ada lebah penjelajah dalam koloni
            }
        }
        return false; // Tidak ada lebah penjelajah dalam koloni
    }
    
    /**
     * Menyimpan solusi sumber makanan terbaik yang ditemukan
     * Bersesuaian dengan langkah eksplisit dalam diagram alir: "Store Best Food Source Solution"
     * 
     * @param population Populasi saat ini
     * @param dataCenterIterator Indeks pusat data saat ini
     */
    private void storeBestFoodSource(Population population, int dataCenterIterator) {
        int dcIndex = dataCenterIterator - 1;
        
        // Cari solusi terbaik dalam populasi saat ini
        double bestFitness = Double.NEGATIVE_INFINITY;
        int bestIndex = -1;
        
        for (int i = 0; i < population.size(); i++) {
            Individual individual = population.getIndividual(i);
            if (individual.getFitness() > bestFitness) {
                bestFitness = individual.getFitness();
                bestIndex = i;
            }
        }
        
        // Perbarui solusi terbaik global jika diperlukan
        if (bestFitness > globalBestFitnesses[dcIndex]) {
            globalBestFitnesses[dcIndex] = bestFitness;
            globalBestPositions[dcIndex] = population.getIndividual(bestIndex).getChromosome().clone();
            System.out.println("Updated Best Food Source with fitness: " + bestFitness);
        }
    }
    
    /**
     * Fase Lebah Pekerja - mencari sumber makanan baru dan mengumpulkan nektar
     * Bersesuaian dengan langkah 7-10 pada pseudocode: "for each employee bees"
     * Dalam fase ini, setiap lebah pekerja mengunjungi sumber makanan dan mencari
     * sumber makanan baru di sekitarnya, menerapkan mekanisme seleksi greedy
     * 
     * @param population Populasi saat ini
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    private void employedBeePhase(Population population, int dataCenterIterator, int cloudletIteration) {
        Random random = new Random();
        
        System.out.println("Employed Bee Phase: Processing " + employedBeeCount + " employed bees");
        
        // Proses hanya untuk lebah pekerja (separuh pertama dari populasi)
        for (int i = 0; i < employedBeeCount; i++) {
            Individual currentBee = population.getIndividual(i);
            
            // Langkah 8: Temukan sumber makanan baru
            // Buat solusi baru dengan memodifikasi solusi saat ini
            int[] newSolution = currentBee.getChromosome().clone();
            
            // Pilih dimensi acak untuk dimodifikasi
            int dimension = random.nextInt(currentBee.getChromosomeLength());
            
            // Pilih sumber makanan lain (tidak termasuk saat ini)
            int partnerIndex;
            do {
                partnerIndex = random.nextInt(employedBeeCount);
            } while (partnerIndex == i);
            
            Individual partner = population.getIndividual(partnerIndex);
            
            // Terapkan batas posisi
            int minPosition = (dataCenterIterator - 1) * 9;
            int maxPosition = ((dataCenterIterator) * 9) - 1;
            
            // Hitung posisi baru menggunakan rumus ABC: vij = xij + φij(xij - xkj)
            int phi = random.nextInt(3) - 1; // Nilai acak antara -1, 0, 1
            int newValue = currentBee.getGene(dimension) + phi * (currentBee.getGene(dimension) - partner.getGene(dimension));
            
            // Pastikan posisi baru berada dalam batas
            if (newValue < minPosition) {
                newValue = minPosition;
            } else if (newValue > maxPosition) {
                newValue = maxPosition;
            }
            
            newSolution[dimension] = newValue;
            
            // Buat solusi baru dan evaluasi
            Individual newBee = new Individual(newSolution);
            double newFitness = calcFitness(newBee, dataCenterIterator, cloudletIteration);
            newBee.setFitness(newFitness);
            
            // Langkah 9: Terapkan mekanisme seleksi greedy
            // Jika lebih baik, gantikan solusi saat ini
            if (newFitness > currentBee.getFitness()) {
                population.setIndividual(i, newBee);
                abandonmentCounter[i] = 0; // Reset penghitung ditinggalkan
                
                // Perbarui solusi terbaik global jika diperlukan
                int dcIndex = dataCenterIterator - 1;
                if (newFitness > globalBestFitnesses[dcIndex]) {
                    globalBestFitnesses[dcIndex] = newFitness;
                    globalBestPositions[dcIndex] = newBee.getChromosome().clone();
                }
            } else {
                // Tingkatkan penghitung ditinggalkan untuk sumber makanan ini
                abandonmentCounter[i]++;
            }
        }
    }
    
    /**
     * Menghitung probabilitas untuk lebah pengamat berdasarkan nilai fitness
     * Bersesuaian dengan langkah 11 pada pseudocode: "Calculate the probability for each food source"
     * Perhitungan ini menentukan bagaimana lebah pengamat akan memilih sumber makanan
     * 
     * @param population Populasi saat ini
     * @return Array probabilitas untuk setiap sumber makanan
     */
    private double[] calculateProbabilities(Population population) {
        double[] probabilities = new double[populationSize];
        double fitnessSum = 0;
        
        // Jumlahkan nilai fitness
        for (int i = 0; i < employedBeeCount; i++) {
            fitnessSum += population.getIndividual(i).getFitness();
        }
        
        // Hitung probabilitas untuk setiap sumber makanan
        for (int i = 0; i < populationSize; i++) {
            if (i < employedBeeCount && fitnessSum > 0) {
                probabilities[i] = population.getIndividual(i).getFitness() / fitnessSum;
            } else {
                probabilities[i] = 0; // Lebah non-pekerja mendapat probabilitas nol
            }
        }
        
        return probabilities;
    }
    
    /**
     * Fase Lebah Pengamat - memilih sumber makanan berdasarkan kualitas dan mencari peningkatan
     * Bersesuaian dengan langkah 12-17 pada pseudocode: "for each onlooker bees"
     * Lebah pengamat memilih sumber makanan berdasarkan fitness mereka,
     * menunjukkan mekanisme umpan balik positif dan negatif
     * 
     * @param population Populasi saat ini
     * @param probabilities Probabilitas setiap sumber makanan
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    private void onlookerBeePhase(Population population, double[] probabilities, int dataCenterIterator, int cloudletIteration) {
        Random random = new Random();
        
        System.out.println("Onlooker Bee Phase: Processing " + onlookerBeeCount + " onlooker bees");
        
        // Indeks awal untuk lebah pengamat
        int onlookerStartIndex = employedBeeCount;
        
        // Proses lebah pengamat
        int onlookerCount = 0;
        
        while (onlookerCount < onlookerBeeCount) {
            // Langkah 13: Pilih sumber makanan berdasarkan probabilitas
            int selectedFoodSource = selectFoodSource(probabilities);
            
            // Langkah 14: Hasilkan sumber makanan baru
            Individual selectedBee = population.getIndividual(selectedFoodSource);
            int[] newSolution = selectedBee.getChromosome().clone();
            
            // Pilih dimensi acak untuk dimodifikasi
            int dimension = random.nextInt(selectedBee.getChromosomeLength());
            
            // Pilih sumber makanan lain (tidak termasuk saat ini)
            int partnerIndex;
            do {
                partnerIndex = random.nextInt(employedBeeCount);
            } while (partnerIndex == selectedFoodSource);
            
            Individual partner = population.getIndividual(partnerIndex);
            
            // Terapkan batas posisi
            int minPosition = (dataCenterIterator - 1) * 9;
            int maxPosition = ((dataCenterIterator) * 9) - 1;
            
            // Hitung posisi baru menggunakan rumus: vij = xij + φij(xij - xkj)
            int phi = random.nextInt(3) - 1; // Nilai acak antara -1, 0, 1
            int newValue = selectedBee.getGene(dimension) + 
                           phi * (selectedBee.getGene(dimension) - partner.getGene(dimension));
            
            // Pastikan posisi baru berada dalam batas
            if (newValue < minPosition) {
                newValue = minPosition;
            } else if (newValue > maxPosition) {
                newValue = maxPosition;
            }
            
            newSolution[dimension] = newValue;
            
            // Langkah 15: Evaluasi fitness
            Individual newBee = new Individual(newSolution);
            double newFitness = calcFitness(newBee, dataCenterIterator, cloudletIteration);
            newBee.setFitness(newFitness);
            
            // Simpan solusi baru di bagian lebah pengamat dari populasi
            int onlookerIndex = onlookerStartIndex + onlookerCount;
            population.setIndividual(onlookerIndex, newBee);
            
            // Langkah 16: Terapkan mekanisme seleksi greedy
            if (newFitness > selectedBee.getFitness()) {
                // Jika lebih baik, gantikan sumber makanan asli - menunjukkan "umpan balik positif"
                population.setIndividual(selectedFoodSource, newBee);
                abandonmentCounter[selectedFoodSource] = 0; // Reset penghitung ditinggalkan
                
                // Perbarui solusi terbaik global jika diperlukan
                int dcIndex = dataCenterIterator - 1;
                if (newFitness > globalBestFitnesses[dcIndex]) {
                    globalBestFitnesses[dcIndex] = newFitness;
                    globalBestPositions[dcIndex] = newBee.getChromosome().clone();
                }
            } else {
                // Jika tidak lebih baik, tingkatkan penghitung ditinggalkan - menunjukkan "umpan balik negatif"
                abandonmentCounter[selectedFoodSource]++;
            }
            
            onlookerCount++;
        }
    }
    
    /**
     * Metode pembantu untuk memilih sumber makanan berdasarkan probabilitas
     * 
     * @param probabilities Array probabilitas untuk setiap sumber makanan
     * @return Indeks sumber makanan yang dipilih
     */
    private int selectFoodSource(double[] probabilities) {
        Random random = new Random();
        double r = random.nextDouble();
        double sum = 0;
        
        // Seleksi roda roulette klasik
        for (int i = 0; i < employedBeeCount; i++) {
            sum += probabilities[i];
            if (r <= sum) {
                return i;
            }
        }
        
        // Fallback - kembalikan lebah pekerja acak
        return random.nextInt(employedBeeCount);
    }
    
    /**
     * Fase Lebah Penjelajah - mencari daerah baru dari ruang solusi
     * Bersesuaian dengan langkah 18-21 pada pseudocode: "Scout Bee phase"
     * Ketika sumber makanan ditinggalkan, lebah penjelajah mencari
     * daerah baru untuk dijelajahi, menunjukkan "Fluktuasi" dalam algoritma
     * 
     * @param population Populasi saat ini
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     */
    private void scoutBeePhase(Population population, int dataCenterIterator, int cloudletIteration) {
        
        // Temukan sumber makanan yang paling banyak ditinggalkan
        int maxAbandonmentIndex = -1;
        int maxAbandonmentCount = -1;
        
        for (int i = 0; i < employedBeeCount; i++) {
            if (abandonmentCounter[i] > maxAbandonmentCount) {
                maxAbandonmentCount = abandonmentCounter[i];
                maxAbandonmentIndex = i;
            }
        }
        
        // Langkah 20: Jika sumber makanan yang paling ditinggalkan melebihi batas, kirim lebah penjelajah ke sumber makanan acak
        // Catatan: Menurut algoritma ABC, hanya ADA SATU lebah penjelajah, jadi hanya ganti sumber yang paling ditinggalkan
        if (maxAbandonmentCount > limit && maxAbandonmentIndex >= 0) {
            System.out.println("Food source at position " + maxAbandonmentIndex + 
                              " abandoned after " + maxAbandonmentCount + " trials (limit: " + limit + ")");
            System.out.println("Scout bee is exploring for new food source");
            
            // Buat solusi acak yang benar-benar baru (Lebah penjelajah menjelajahi area baru)
            Individual scout = new Individual(population.getIndividual(maxAbandonmentIndex).getChromosomeLength(), 
                                             dataCenterIterator);
            
            // Evaluasi solusi baru
            double scoutFitness = calcFitness(scout, dataCenterIterator, cloudletIteration);
            scout.setFitness(scoutFitness);
            
            // Gantikan sumber makanan yang ditinggalkan
            population.setIndividual(maxAbandonmentIndex, scout);
            abandonmentCounter[maxAbandonmentIndex] = 0;
            
            System.out.println("Scout bee found new food source with fitness: " + scoutFitness);
            
            // Perbarui solusi terbaik global jika diperlukan - menunjukkan "Interaksi Berganda"
            int dcIndex = dataCenterIterator - 1;
            if (scoutFitness > globalBestFitnesses[dcIndex]) {
                globalBestFitnesses[dcIndex] = scoutFitness;
                globalBestPositions[dcIndex] = scout.getChromosome().clone();
                System.out.println("New food source is better than current best - information shared with colony");
            }
        } else {
            System.out.println("No food sources abandoned this iteration");
        }
    }

    /**
     * Memilih sejumlah "eliteCount" individu terbaik dari populasi berdasarkan fitness
     * Bersesuaian dengan langkah 7 dalam pseudocode EOABC: "Choose EN elite solutions from the current population"
     * 
     * @param population Populasi saat ini
     * @param eliteCount Jumlah individu elit yang akan dipilih
     * @return Array yang berisi individu elit
     */
    private Individual[] selectEliteSolutions(Population population, int eliteCount) {
        // Membuat salinan individu untuk diurutkan
        Individual[] individuals = Arrays.copyOf(population.getIndividuals(), population.size());
        
        // Urutkan berdasarkan fitness secara menurun (fitness lebih tinggi lebih baik)
        Arrays.sort(individuals, new Comparator<Individual>() {
            @Override
            public int compare(Individual ind1, Individual ind2) {
                return Double.compare(ind2.getFitness(), ind1.getFitness());
            }
        });
        
        // Kembalikan individu terbaik sebanyak eliteCount
        return Arrays.copyOf(individuals, eliteCount);
    }

    /**
     * Membuat solusi berbasis oposisi elit
     * Bersesuaian dengan langkah 13-14 dalam pseudocode EOABC: "Create the elite opposition-based solution EOi for the ith solution Xi"
     * 
     * @param original Individu asli
     * @param eliteSolutions Array solusi elit
     * @param minPosition Posisi minimum
     * @param maxPosition Posisi maksimum
     * @param k Nilai acak untuk pemilihan elit
     * @return Individu baru hasil oposisi elit
     */
    private Individual createEliteOpposition(
        Individual original, Individual[] eliteSolutions, int minPosition, int maxPosition, double k) {
        
        Random random = new Random();
        int[] chromosome = original.getChromosome().clone();
        int dimensions = chromosome.length;
        
        // Pilih solusi elit secara acak menggunakan k sebagai panduan probabilitas
        int eliteIndex = (int)(k * eliteSolutions.length);
        if (eliteIndex >= eliteSolutions.length) {
            eliteIndex = eliteSolutions.length - 1;
        }
        Individual eliteSolution = eliteSolutions[eliteIndex];
        
        // Terapkan oposisi elit untuk setiap dimensi
        for (int i = 0; i < dimensions; i++) {
            // Rumus untuk oposisi elit: opposite = (lb + ub) - original
            // Dengan pengaruh dari solusi elit
            int opposite = (minPosition + maxPosition) - original.getGene(i);
            
            // Gabungkan dengan solusi elit menggunakan koefisien d
            // Ini adalah bagian yang ditingkatkan dari EOBL di mana kita menggunakan solusi elit untuk memandu oposisi
            int newValue = (int)((1-d) * opposite + d * eliteSolution.getGene(i));
            
            // Pastikan nilai berada dalam batas
            if (newValue < minPosition) {
                newValue = minPosition;
            } else if (newValue > maxPosition) {
                newValue = maxPosition;
            }
            
            chromosome[i] = newValue;
        }
        
        return new Individual(chromosome);
    }

    /**
     * Menggabungkan populasi saat ini dengan populasi oposisi dan memilih individu terbaik
     * Bersesuaian dengan langkah 19 dalam pseudocode EOABC: "Choose the top best SN solutions from {P, EOP} for the next generation"
     * 
     * @param currentPopulation Populasi saat ini
     * @param oppositionPopulation Populasi oposisi
     */
    private void mergeAndSelectBest(Population currentPopulation, List<Individual> oppositionPopulation) {
        // Buat daftar untuk menyimpan semua individu
        List<Individual> combinedPopulation = new ArrayList<>();
        
        // Tambahkan semua individu populasi saat ini
        for (Individual ind : currentPopulation.getIndividuals()) {
            combinedPopulation.add(ind);
        }
        
        // Tambahkan semua individu populasi oposisi
        combinedPopulation.addAll(oppositionPopulation);
        
        // Urutkan berdasarkan fitness secara menurun (fitness lebih tinggi lebih baik)
        Collections.sort(combinedPopulation, new Comparator<Individual>() {
            @Override
            public int compare(Individual ind1, Individual ind2) {
                return Double.compare(ind2.getFitness(), ind1.getFitness());
            }
        });
        
        // Pilih individu terbaik sebanyak ukuran populasi
        for (int i = 0; i < currentPopulation.size(); i++) {
            if (i < combinedPopulation.size()) {
                currentPopulation.setIndividual(i, combinedPopulation.get(i));
            }
        }
    }

    /**
     * Menghitung fitness berdasarkan keadilan sistem (Persamaan 1)
     * Fungsi ini mengevaluasi kualitas solusi berdasarkan makespan dan biaya
     * 
     * @param individual Individu yang akan dievaluasi
     * @param dataCenterIterator Indeks pusat data saat ini
     * @param cloudletIteration Iterasi cloudlet saat ini
     * @return Nilai fitness dari individu
     */
    public double calcFitness(Individual individual, int dataCenterIterator, int cloudletIteration) {
        double totalExecutionTime = 0;
        double totalCost = 0;
        int iterator = 0;
        dataCenterIterator = dataCenterIterator - 1;

        // Hitung waktu eksekusi total dan biaya untuk semua cloudlet
        for (int i = 0 + dataCenterIterator * 9 + cloudletIteration * 54;
             i < 9 + dataCenterIterator * 9 + cloudletIteration * 54; i++) {
            int gene = individual.getGene(iterator);
            double mips = calculateMips(gene % 9);

            totalExecutionTime += cloudletList.get(i).getCloudletLength() / mips;
            totalCost += calculateCost(vmList.get(gene % 9), cloudletList.get(i));
            iterator++;
        }

        // Hitung fitness untuk makespan dan biaya
        double makespanFitness = calculateMakespanFitness(totalExecutionTime);
        double costFitness = calculateCostFitness(totalCost);
        
        // Gabungkan fitness makespan dan biaya
        double fitness = makespanFitness + costFitness;

        return fitness;
    }

    /**
     * Menghitung MIPS (Million Instructions Per Second) berdasarkan indeks VM
     * 
     * @param vmIndex Indeks VM
     * @return Nilai MIPS
     */
    private double calculateMips(int vmIndex) {
        double mips = 0;
        if (vmIndex % 9 == 0 || vmIndex % 9 == 3 || vmIndex % 9 == 6) {
            mips = 400;
        } else if (vmIndex % 9 == 1 || vmIndex % 9 == 4 || vmIndex % 9 == 7) {
            mips = 500;
        } else if (vmIndex % 9 == 2 || vmIndex % 9 == 5 || vmIndex % 9 == 8) {
            mips = 600;
        }
        return mips;
    }

    /**
     * Menghitung biaya eksekusi cloudlet pada VM tertentu
     * 
     * @param vm VM yang akan digunakan
     * @param cloudlet Cloudlet yang akan dieksekusi
     * @return Biaya eksekusi
     */
    private double calculateCost(Vm vm, Cloudlet cloudlet) {
      double costPerMips = vm.getCostPerMips();
      double cloudletLength = cloudlet.getCloudletLength();
      double mips = vm.getMips();
      double executionTime = cloudletLength / mips;
      return costPerMips * executionTime;
    }

    /**
     * Menghitung fitness berdasarkan makespan (waktu eksekusi)
     * Semakin rendah makespan, semakin tinggi fitness
     * 
     * @param totalExecutionTime Total waktu eksekusi
     * @return Nilai fitness untuk makespan
     */
    private double calculateMakespanFitness(double totalExecutionTime) {
      // Semakin tinggi makespan, semakin rendah fitness
      return 1.0 / totalExecutionTime;
    }

    /**
     * Menghitung fitness berdasarkan biaya
     * Semakin rendah biaya, semakin tinggi fitness
     * 
     * @param totalCost Total biaya
     * @return Nilai fitness untuk biaya
     */
    private double calculateCostFitness(double totalCost) {
      // Semakin rendah biaya, semakin tinggi fitness
      return 1.0 / totalCost;
    }

    /**
     * Mendapatkan alokasi VM terbaik untuk pusat data tertentu
     * 
     * @param dataCenterIterator Indeks pusat data
     * @return Array alokasi VM terbaik
     */
    public int[] getBestVmAllocationForDatacenter(int dataCenterIterator) {
        return globalBestPositions[dataCenterIterator - 1];
    }

    /**
     * Mendapatkan nilai fitness terbaik untuk pusat data tertentu
     * 
     * @param dataCenterIterator Indeks pusat data
     * @return Nilai fitness terbaik
     */
    public double getBestFitnessForDatacenter(int dataCenterIterator) {
        return globalBestFitnesses[dataCenterIterator - 1];
    }
}