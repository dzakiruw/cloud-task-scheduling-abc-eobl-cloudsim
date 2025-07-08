package org.cloudbus.cloudsim.examples;

import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

/**
 * Implementation of the Genetic Algorithm for VM allocation in cloud computing.
 * This algorithm follows the pseudocode provided:
 * 
 * Input: Population Size, n
 *        Maximum number of iterations, MAX
 * Output: Global best solution, Ypt
 * begin
 *   Generate initial population of n chromosomes Y, (i=1,2, .... ,n)
 *   Set iteration counter t = 0
 *   Compute the fitness value of each chromosomes
 *   while (t < MAX)
 *     Select a pair of chromosomes from initial population based on fitness
 *     Apply crossover operation on selected pair with crossover probability
 *     Apply mutation on the offspring with mutation probability
 *     Replace old population with newly generated population
 *     Increment the current iteration t by 1.
 *   end while
 *   return the best solution, Ypt
 * end
 */
public class GeneticAlgorithm {
    private int maxIterations; // Maximum number of iterations
    private int populationSize; // Population size (number of chromosomes)
    private double crossoverProbability; // Probability of crossover
    private double mutationProbability; // Probability of mutation
    private List<Cloudlet> cloudletList; // List of cloudlets (tasks)
    private List<Vm> vmList; // List of VMs
    private int numberOfDataCenters = 6; // Number of datacenters
    private double[] globalBestFitnesses; // Best fitness values for each datacenter
    private int[][] globalBestPositions; // Best positions (solutions) for each datacenter

    /**
     * Constructor
     * @param maxIterations Maximum number of iterations
     * @param populationSize Population size
     * @param crossoverProbability Probability of crossover
     * @param mutationProbability Probability of mutation
     * @param cloudletList List of cloudlets
     * @param vmList List of VMs
     * @param chromosomeLength Length of each chromosome
     */
    public GeneticAlgorithm(int maxIterations, int populationSize, double crossoverProbability, 
                           double mutationProbability, List<Cloudlet> cloudletList, List<Vm> vmList, 
                           int chromosomeLength) {
        this.maxIterations = maxIterations;
        this.populationSize = populationSize;
        this.crossoverProbability = crossoverProbability;
        this.mutationProbability = mutationProbability;
        this.cloudletList = cloudletList;
        this.vmList = vmList;

        globalBestFitnesses = new double[numberOfDataCenters];
        globalBestPositions = new int[numberOfDataCenters][];

        for (int i = 0; i < numberOfDataCenters; i++) {
            // Initialize best fitness as negative infinity
            globalBestFitnesses[i] = Double.NEGATIVE_INFINITY;
            // Array to store best positions for each datacenter
            globalBestPositions[i] = new int[chromosomeLength];
        }
    }

    /**
     * Initialize population
     * @param chromosomeLength Length of each chromosome
     * @param dataCenterIterator Index of the datacenter being processed
     * @return The initialized population
     */
    public PopulationGA initPopulation(int chromosomeLength, int dataCenterIterator) {
        // Create a new population
        PopulationGA population = new PopulationGA(this.populationSize, chromosomeLength, dataCenterIterator);
        return population;
    }

    /**
     * Compute fitness for all chromosomes in the population
     * @param population The population
     * @param dataCenterIterator Index of the datacenter being processed
     */
    public void computeFitness(PopulationGA population, int dataCenterIterator) {
        for (Chromosome chromosome : population.getChromosomes()) {
            double fitness = calcFitness(chromosome, dataCenterIterator, 0);
            chromosome.setFitness(fitness);
        }
    }

    /**
     * Select a pair of chromosomes using tournament selection
     * @param population The population
     * @return An array containing two selected chromosomes
     */
    public Chromosome[] selectParents(PopulationGA population) {
        Random random = new Random();
        Chromosome[] parents = new Chromosome[2];
        
        // Tournament selection
        for (int i = 0; i < 2; i++) {
            // Select 3 random chromosomes for tournament
            Chromosome best = null;
            double bestFitness = Double.NEGATIVE_INFINITY;
            
            for (int j = 0; j < 3; j++) {
                int index = random.nextInt(population.getChromosomes().size());
                Chromosome candidate = population.getChromosome(index);
                
                if (candidate.getFitness() > bestFitness) {
                    bestFitness = candidate.getFitness();
                    best = candidate;
                }
            }
            
            parents[i] = best;
        }
        
        return parents;
    }

    /**
     * Apply crossover operation on selected parents
     * @param parent1 First parent
     * @param parent2 Second parent
     * @return An array containing two offspring
     */
    public Chromosome[] crossover(Chromosome parent1, Chromosome parent2) {
        Random random = new Random();
        Chromosome[] offspring = new Chromosome[2];
        
        // Create offspring with same IDs as parents
        offspring[0] = new Chromosome(parent1.getId(), new int[parent1.getChromosomeLength()]);
        offspring[1] = new Chromosome(parent2.getId(), new int[parent2.getChromosomeLength()]);
        
        // Apply crossover with probability
        if (random.nextDouble() < crossoverProbability) {
            // Single-point crossover
            int crossoverPoint = random.nextInt(parent1.getChromosomeLength());
            
            // Copy genes from parents to offspring
            for (int i = 0; i < parent1.getChromosomeLength(); i++) {
                if (i < crossoverPoint) {
                    offspring[0].setGene(i, parent1.getGene(i));
                    offspring[1].setGene(i, parent2.getGene(i));
                } else {
                    offspring[0].setGene(i, parent2.getGene(i));
                    offspring[1].setGene(i, parent1.getGene(i));
                }
            }
        } else {
            // No crossover, copy parents directly
            for (int i = 0; i < parent1.getChromosomeLength(); i++) {
                offspring[0].setGene(i, parent1.getGene(i));
                offspring[1].setGene(i, parent2.getGene(i));
            }
        }
        
        return offspring;
    }

    /**
     * Apply mutation operation on offspring
     * @param offspring The offspring to mutate
     * @param dataCenterIterator Index of the datacenter being processed
     */
    public void mutate(Chromosome offspring, int dataCenterIterator) {
        Random random = new Random();
        
        // Apply mutation with probability for each gene
        for (int i = 0; i < offspring.getChromosomeLength(); i++) {
            if (random.nextDouble() < mutationProbability) {
                // Generate a new random value for the gene
                int minPosition = (dataCenterIterator - 1) * 9;
                int maxPosition = (dataCenterIterator * 9) - 1;
                int newValue = minPosition + random.nextInt(maxPosition - minPosition + 1);
                offspring.setGene(i, newValue);
            }
        }
    }

    /**
     * Run the genetic algorithm
     * @param population The initial population
     * @param dataCenterIterator Index of the datacenter being processed
     */
    public void runGA(PopulationGA population, int dataCenterIterator) {
        int iteration = 0;
        
        // Compute initial fitness
        computeFitness(population, dataCenterIterator);
        
        // Main iteration loop
        while (iteration < maxIterations) {
            // Create a new population for the next generation
            List<Chromosome> newPopulation = new java.util.ArrayList<>();
            
            // Elitism: Keep the best chromosome
            population.sortByFitness();
            newPopulation.add(population.getChromosome(0).clone());
            
            // Generate the rest of the new population
            while (newPopulation.size() < population.getPopulationSize()) {
                // Select parents
                Chromosome[] parents = selectParents(population);
                
                // Apply crossover
                Chromosome[] offspring = crossover(parents[0], parents[1]);
                
                // Apply mutation
                mutate(offspring[0], dataCenterIterator);
                mutate(offspring[1], dataCenterIterator);
                
                // Add offspring to new population
                newPopulation.add(offspring[0]);
                if (newPopulation.size() < population.getPopulationSize()) {
                    newPopulation.add(offspring[1]);
                }
            }
            
            // Replace old population with new population
            population.setChromosomes(newPopulation);
            
            // Compute fitness for new population
            computeFitness(population, dataCenterIterator);
            
            // Sort population by fitness
            population.sortByFitness();
            
            // Update global best if necessary
            int dcIndex = dataCenterIterator - 1;
            if (population.getChromosome(0).getFitness() > globalBestFitnesses[dcIndex]) {
                globalBestFitnesses[dcIndex] = population.getChromosome(0).getFitness();
                globalBestPositions[dcIndex] = population.getChromosome(0).getGenes().clone();
            }
            
            // Increment iteration counter
            iteration++;
        }
    }

    /**
     * Calculate fitness for a chromosome
     * @param chromosome The chromosome
     * @param dataCenterIterator Index of the datacenter being processed
     * @param cloudletIteration Index of the cloudlet iteration
     * @return The fitness value
     */
    public double calcFitness(Chromosome chromosome, int dataCenterIterator, int cloudletIteration) {
        double totalExecutionTime = 0;
        double totalCost = 0;
        int iterator = 0;
        dataCenterIterator = dataCenterIterator - 1;

        for (int i = 0 + dataCenterIterator * 9 + cloudletIteration * 54;
             i < 9 + dataCenterIterator * 9 + cloudletIteration * 54; i++) {
            int gene = chromosome.getGene(iterator);
            double mips = calculateMips(gene % 9);

            totalExecutionTime += cloudletList.get(i).getCloudletLength() / mips;
            totalCost += calculateCost(vmList.get(gene % 9), cloudletList.get(i));
            iterator++;
        }

        double makespanFitness = calculateMakespanFitness(totalExecutionTime);
        double costFitness = calculateCostFitness(totalCost);

        double fitness = makespanFitness + costFitness;

        chromosome.setFitness(fitness);
        return fitness;
    }

    /**
     * Calculate MIPS for a VM
     * @param vmIndex Index of the VM
     * @return The MIPS value
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
     * Calculate cost for a VM-cloudlet pair
     * @param vm The VM
     * @param cloudlet The cloudlet
     * @return The cost
     */
    private double calculateCost(Vm vm, Cloudlet cloudlet) {
        double costPerMips = vm.getCostPerMips();
        double cloudletLength = cloudlet.getCloudletLength();
        double mips = vm.getMips();
        double executionTime = cloudletLength / mips;
        return costPerMips * executionTime;
    }

    /**
     * Calculate makespan fitness
     * @param totalExecutionTime Total execution time
     * @return The makespan fitness
     */
    private double calculateMakespanFitness(double totalExecutionTime) {
        return 1.0 / totalExecutionTime;
    }

    /**
     * Calculate cost fitness
     * @param totalCost Total cost
     * @return The cost fitness
     */
    private double calculateCostFitness(double totalCost) {
        return 1.0 / totalCost;
    }

    /**
     * Get the best VM allocation for a datacenter
     * @param dataCenterIterator Index of the datacenter
     * @return The best VM allocation
     */
    public int[] getBestVmAllocationForDatacenter(int dataCenterIterator) {
        return globalBestPositions[dataCenterIterator - 1];
    }

    /**
     * Get the best fitness for a datacenter
     * @param dataCenterIterator Index of the datacenter
     * @return The best fitness
     */
    public double getBestFitnessForDatacenter(int dataCenterIterator) {
        return globalBestFitnesses[dataCenterIterator - 1];
    }
} 