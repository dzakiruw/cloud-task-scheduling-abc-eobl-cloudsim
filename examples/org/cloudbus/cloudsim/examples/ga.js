const { cloneDeep } = require('lodash');

// Constants for GA parameters - following GeneticAlgorithm.java
const DEFAULT_POPULATION_SIZE = 30;
const DEFAULT_ITERATIONS = 15;
const DEFAULT_CROSSOVER_PROBABILITY = 0.9;
const DEFAULT_MUTATION_PROBABILITY = 0.1;

// Cost constants for GA optimization (following CloudSim approach)
const COST_PER_MIPS = 0.5;
const COST_PER_TASKLENGTH = 0.1;

// Hardcoded task length values based on task weight (like CloudSim approach)
// Used only for GA algorithm fitness calculation
const TASK_LENGTH_CONFIG = {
  ringan: 45,    // Light tasks: minimal task length
  sedang: 350,   // Medium tasks: moderate task length  
  berat: 1150    // Heavy tasks: high task length
};

// Cache for fitness calculations to prevent redundant calculations
const fitnessCache = new Map();

// Utility function for random integer generation (same as abc.js)
function getRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
  }
  
// GA fitness calculation - using same approach as abc.js
function estimateFitness(chromosome, tasks, workerMipsArray) {
  const position = chromosome.getGenes();
  const cacheKey = position.join(',');
  if (fitnessCache.has(cacheKey)) {
    const cachedFitness = fitnessCache.get(cacheKey);
    chromosome.setFitness(cachedFitness);
    return cachedFitness;
  }
  
  let totalExec = 0;
  let totalCost = 0;

  for (let i = 0; i < chromosome.getChromosomeLength(); i++) {
    const workerIndex = chromosome.getGene(i);
      const task = tasks[i];
      
      if (!task) continue;
      
    // Get task length
    let taskLength = task.length || task.mi || task.MI || 1000;
      
    // Use real MIPS from worker (same as abc.js)
    const mips = workerMipsArray[workerIndex] || 500;
      const execTime = taskLength / mips;
      
    // Get task length modifier based on task weight (same as abc.js)
    const taskWeight = task.weight || 'sedang';
    const taskLengthModifier = TASK_LENGTH_CONFIG[taskWeight] || TASK_LENGTH_CONFIG.sedang;
    
    // Cost calculation for GA optimization: execution cost + task length cost
    const computationalCost = execTime * COST_PER_MIPS;
    const taskLengthCost = taskLengthModifier * COST_PER_TASKLENGTH;
    const cost = computationalCost + taskLengthCost;

      totalExec += execTime;
      totalCost += cost;
  }
  
  // Fitness calculation: higher fitness = better solution (same as abc.js)
  const makespanFitness = 1 / totalExec;
  const costFitness = 1 / totalCost;
  const fitness = makespanFitness + costFitness;
  
  fitnessCache.set(cacheKey, fitness);
  chromosome.setFitness(fitness);
  return fitness;
}

/**
 * Chromosome class representing a single solution
 * Following the structure from GeneticAlgorithm.java
 * Updated to use real worker count like abc.js
 */
class Chromosome {
  constructor(id, chromosomeLength, workerCount) {
    this.id = id;
    this.genes = new Array(chromosomeLength);
    this.fitness = -Infinity;
    this.chromosomeLength = chromosomeLength;
    this.workerCount = workerCount || 6; // Default to 6 workers
    
    // Initialize with random genes (worker indices)
    for (let i = 0; i < chromosomeLength; i++) {
      this.genes[i] = Math.floor(Math.random() * this.workerCount);
    }
  }
  
  getGene(index) {
    return this.genes[index];
  }
  
  setGene(index, value) {
    this.genes[index] = value;
  }
  
  getGenes() {
    return this.genes;
  }
  
  getFitness() {
    return this.fitness;
  }
  
  setFitness(fitness) {
    this.fitness = fitness;
      }
  
  getChromosomeLength() {
    return this.chromosomeLength;
  }
  
  getId() {
    return this.id;
}

  clone() {
    const cloned = new Chromosome(this.id, this.chromosomeLength, this.workerCount);
    cloned.genes = [...this.genes];
    cloned.fitness = this.fitness;
    return cloned;
  }
}

/**
 * PopulationGA class representing a population of chromosomes
 * Following the structure from GeneticAlgorithm.java
 */
class PopulationGA {
  constructor(populationSize, chromosomeLength, dataCenterIterator, workerCount) {
    this.populationSize = populationSize;
    this.chromosomeLength = chromosomeLength;
    this.dataCenterIterator = dataCenterIterator;
    this.workerCount = workerCount;
    this.chromosomes = [];
    
    // Generate initial population of n chromosomes
    for (let i = 0; i < populationSize; i++) {
      this.chromosomes.push(new Chromosome(i, chromosomeLength, workerCount));
    }
  }
  
  getChromosomes() {
    return this.chromosomes;
  }
  
  setChromosomes(chromosomes) {
    this.chromosomes = chromosomes;
      }
  
  getChromosome(index) {
    return this.chromosomes[index];
  }
  
  getPopulationSize() {
    return this.populationSize;
  }
  
  sortByFitness() {
    this.chromosomes.sort((a, b) => b.fitness - a.fitness);
  }
}

/**
 * Genetic Algorithm implementation following GeneticAlgorithm.java pseudocode
 * Updated to use real MIPS values like abc.js
 */
class GeneticAlgorithm {
  constructor(workerCount, workerMipsArray) {
    this.workerCount = workerCount;
    this.workerMipsArray = workerMipsArray; // Real MIPS values from workers
    
    // GA parameters (same as abc.js structure)
    this.populationSize = DEFAULT_POPULATION_SIZE;
    this.maxIterations = DEFAULT_ITERATIONS;
    this.crossoverProbability = DEFAULT_CROSSOVER_PROBABILITY;
    this.mutationProbability = DEFAULT_MUTATION_PROBABILITY;
    
    // Tracking variables
    this.bestSolution = null;
    this.bestFitness = -Infinity;
  }
  
  /**
   * Initialize population - following pseudocode step 1
   * @param {number} chromosomeLength Length of each chromosome
   * @return {PopulationGA} The initialized population
   */
  initPopulation(chromosomeLength) {
    return new PopulationGA(this.populationSize, chromosomeLength, 1, this.workerCount);
  }
  
  /**
   * Compute the fitness value of each chromosomes - following pseudocode step 2
   * @param {PopulationGA} population The population
   * @param {Array} tasks The tasks to process
   */
  computeFitness(population, tasks) {
    for (const chromosome of population.getChromosomes()) {
      const fitness = estimateFitness(chromosome, tasks, this.workerMipsArray);
      chromosome.setFitness(fitness);
    }
  }
  
  /**
   * Select a pair of chromosomes from initial population based on fitness
   * Using tournament selection like Java implementation
   * @param {PopulationGA} population The population
   * @return {Array} An array containing two selected chromosomes
   */
  selectParents(population) {
    const parents = [];
    
    // Select 2 parents using tournament selection
    for (let i = 0; i < 2; i++) {
      let best = null;
      let bestFitness = -Infinity;
    
      // Tournament with 3 candidates
      for (let j = 0; j < 3; j++) {
        const index = Math.floor(Math.random() * population.getChromosomes().length);
        const candidate = population.getChromosome(index);
        
        if (candidate.getFitness() > bestFitness) {
          bestFitness = candidate.getFitness();
          best = candidate;
        }
      }
      
      parents.push(best);
    }
    
    return parents;
  }
  
  /**
   * Apply crossover operation on selected pair with crossover probability
   * @param {Chromosome} parent1 First parent
   * @param {Chromosome} parent2 Second parent
   * @return {Array} An array containing two offspring
   */
  crossover(parent1, parent2) {
    const offspring = [
      new Chromosome(parent1.getId(), parent1.getChromosomeLength(), parent1.workerCount),
      new Chromosome(parent2.getId(), parent2.getChromosomeLength(), parent2.workerCount)
    ];
    
    // Apply crossover with probability
    if (Math.random() < this.crossoverProbability) {
      // Single-point crossover
      const crossoverPoint = Math.floor(Math.random() * parent1.getChromosomeLength());
      
      // Copy genes from parents to offspring
      for (let i = 0; i < parent1.getChromosomeLength(); i++) {
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
      for (let i = 0; i < parent1.getChromosomeLength(); i++) {
        offspring[0].setGene(i, parent1.getGene(i));
        offspring[1].setGene(i, parent2.getGene(i));
      }
    }
    
    return offspring;
  }
  
  /**
   * Apply mutation on the offspring with mutation probability
   * @param {Chromosome} offspring The offspring to mutate
   */
  mutate(offspring) {
    // Apply mutation with probability for each gene (simplified like abc.js)
    for (let i = 0; i < offspring.getChromosomeLength(); i++) {
      if (Math.random() < this.mutationProbability) {
        // Generate a new random worker index (0 to workerCount-1)
        const newValue = Math.floor(Math.random() * this.workerCount);
        offspring.setGene(i, newValue);
      }
      }
    }
    
  /**
   * Main GA algorithm following the exact pseudocode
   * @param {Array} tasks The tasks to process
   */
  run(tasks) {
    console.log("Starting Genetic Algorithm");
    
    // Generate initial population of n chromosomes
    const population = this.initPopulation(tasks.length);
    
    // Set iteration counter t = 0
    let iteration = 0;
    
    // Compute the fitness value of each chromosomes
    this.computeFitness(population, tasks);
    
    console.log(`GA Algorithm starting with ${this.populationSize} chromosomes for ${this.maxIterations} iterations`);
    
    // while (t < MAX)
    while (iteration < this.maxIterations) {
      console.log(`\n========== GA ITERATION ${iteration + 1} ==========`);
      
      // Create a new population for the next generation
        const newPopulation = [];
        
      // Elitism: Keep the best chromosome
      population.sortByFitness();
      newPopulation.push(population.getChromosome(0).clone());
        
      // Generate the rest of the new population
      while (newPopulation.length < population.getPopulationSize()) {
        // Select a pair of chromosomes from initial population based on fitness
        const parents = this.selectParents(population);
          
        // Apply crossover operation on selected pair with crossover probability
        const offspring = this.crossover(parents[0], parents[1]);
          
        // Apply mutation on the offspring with mutation probability
        this.mutate(offspring[0]);
        this.mutate(offspring[1]);
          
          // Add offspring to new population
          newPopulation.push(offspring[0]);
        if (newPopulation.length < population.getPopulationSize()) {
            newPopulation.push(offspring[1]);
          }
        }
        
      // Replace old population with newly generated population
      population.setChromosomes(newPopulation);
        
      // Compute fitness for new population
      this.computeFitness(population, tasks);
      
      // Sort population by fitness
      population.sortByFitness();
      
      // Update global best if necessary
      const bestChromosome = population.getChromosome(0);
      if (bestChromosome.getFitness() > this.bestFitness) {
        this.bestFitness = bestChromosome.getFitness();
        this.bestSolution = [...bestChromosome.getGenes()];
        
        console.log(`Updated Best Fitness: ${bestChromosome.getFitness().toFixed(6)}`);
        }
      
      // Increment the current iteration t by 1
      iteration++;
    }
    
    console.log("\nGA ALGORITHM COMPLETED:");
    console.log("Best fitness: " + this.bestFitness);
    
    // Ensure we return a valid solution
    if (this.bestSolution === null) {
      console.log("Warning: No valid solution found. Returning default solution.");
      this.bestSolution = Array(tasks.length).fill(0);
    }
    
    return this.bestSolution;
  }
}

/**
 * Run the Genetic Algorithm for task scheduling - main interface function
 * Following the same approach as abc.js for consistency
 * @param {number} taskCount Number of tasks
 * @param {number} workerCount Number of workers
 * @param {Array} tasks Array of tasks with their properties
 * @param {Array} workerMipsArray Array of real MIPS values from workers
 * @param {boolean} useAdvanced Whether to use advanced GA features (unused for now)
 * @return {Array} Best solution found
 */
function runGeneticAlgorithm(taskCount, workerCount, tasks, workerMipsArray, useAdvanced = false) {
  // Basic input validation (same as abc.js)
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return Array(taskCount).fill(0);
  }
  
  if (!Array.isArray(workerMipsArray) || workerMipsArray.length !== workerCount) {
    return Array(taskCount).fill(0);
  }
  
  // Simplified task processing - just ensure tasks have length property (same as abc.js)
  const validatedTasks = tasks.map((task, i) => ({
    name: task?.name || `Task-${i}`,
    length: task?.length || task?.mi || task?.MI || 1000,
    weight: task?.weight || 'sedang'
  }));
  
  console.log("🧬 Initializing Genetic Algorithm");
  
  // Create GA instance
  const ga = new GeneticAlgorithm(workerCount, workerMipsArray);
  const solution = ga.run(validatedTasks);
  
  // Return valid solution or default fallback (same as abc.js)
  if (!Array.isArray(solution) || solution.length !== taskCount) {
    return solution?.length < taskCount 
      ? [...solution, ...Array(taskCount - solution.length).fill(0)]
      : solution?.slice(0, taskCount) || Array(taskCount).fill(0);
  }
  
  return solution;
}

module.exports = { 
  runGeneticAlgorithm,
  GeneticAlgorithm,
  Chromosome,
  PopulationGA,
  estimateFitness,
  getRandomInt,
  COST_PER_MIPS,
  COST_PER_TASKLENGTH,
  TASK_LENGTH_CONFIG,
  DEFAULT_POPULATION_SIZE,
  DEFAULT_ITERATIONS,
  DEFAULT_CROSSOVER_PROBABILITY,
  DEFAULT_MUTATION_PROBABILITY
}; 