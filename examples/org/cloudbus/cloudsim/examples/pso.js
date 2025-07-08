// pso.js - PSO Algorithm in Node.js (fitness: makespan + cost based on Java version)

// Constants for PSO parameters
const DEFAULT_POPULATION_SIZE = 30;
const DEFAULT_ITERATIONS = 15;
const DEFAULT_W = 0.5; // Inertia weight
const DEFAULT_L1 = 1.5; // Cognitive component
const DEFAULT_L2 = 1.5; // Social component

// Cost constants for PSO optimization (following CloudSim approach)
const COST_PER_MIPS = 0.5;
const COST_PER_TASKLENGTH = 0.1;

// Hardcoded task length values based on task weight (like CloudSim approach)
// Used only for PSO algorithm fitness calculation
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

// PSO fitness calculation - simplified version without personal best update
function calcFitness(individual, tasks, workerMipsArray) {
  const position = individual.chromosome;
  const cacheKey = position.join(',');
  if (fitnessCache.has(cacheKey)) {
    return fitnessCache.get(cacheKey);
  }
  
  let totalExec = 0;
  let totalCost = 0;
  
  for (let i = 0; i < position.length; i++) {
    const workerIndex = position[i];
    const task = tasks[i];
    let taskLength = task.length || task.mi || task.MI || 1000;
    
    // Use real MIPS from worker (same as abc.js)
    const mips = workerMipsArray[workerIndex] || 500;
    const execTime = taskLength / mips;
    
    // Get task length modifier based on task weight (hardcoded like CloudSim)
    const taskWeight = task.weight || 'sedang';
    const taskLengthModifier = TASK_LENGTH_CONFIG[taskWeight] || TASK_LENGTH_CONFIG.sedang;
    
    // Cost calculation for PSO optimization: execution cost + task length cost
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
  return fitness;
}

// Create PSO particle (following abc.js createIndividual pattern)
function createParticle(taskCount, workerCount) {
  const chromosome = Array.from({ length: taskCount }, () => getRandomInt(0, workerCount - 1));
  const velocity = Array.from({ length: taskCount }, () => (Math.random() - 0.5) * workerCount);
  return {
    chromosome,
    velocity,
    personalBest: [...chromosome],
    personalBestFitness: -Infinity,
    fitness: -Infinity,
  };
}

// PSO Algorithm implementation following exact PSO.java structure
class PSOStandard {
  constructor(workerCount, workerMipsArray) {
    this.workerCount = workerCount;
    this.workerMipsArray = workerMipsArray;
    
    // PSO parameters (same as abc.js structure)
    this.populationSize = DEFAULT_POPULATION_SIZE;
    this.maxIterations = DEFAULT_ITERATIONS;
    this.w = DEFAULT_W; // Inertia weight
    this.l1 = DEFAULT_L1; // Cognitive component
    this.l2 = DEFAULT_L2; // Social component
    
    // Global best tracking
    this.globalBestFitness = -Infinity;
    this.globalBestPosition = null;
  }

  // Step 3: Initialize population (following PSO.java initPopulation)
  initPopulation(taskCount) {
    const population = Array.from({ length: this.populationSize }, () => 
      createParticle(taskCount, this.workerCount)
    );
    return population;
  }

  // Step 4: Evaluate fitness + Step 5: Update personal best + Step 6: Update global best
  // Following exact PSO.java evaluateFitness structure
  evaluateFitness(population, tasks) {
    for (const individual of population) {
      // Calculate fitness for individual
      const fitness = calcFitness(individual, tasks, this.workerMipsArray);
      individual.fitness = fitness;

      // Step 5: Update personal best (following PSO.java logic)
      if (fitness > individual.personalBestFitness) {
        individual.personalBestFitness = fitness;
        individual.personalBest = [...individual.chromosome];
      }

      // Step 6: Update global best (following PSO.java logic)
      if (fitness > this.globalBestFitness) {
        this.globalBestFitness = fitness;
        this.globalBestPosition = [...individual.chromosome];
      }
    }
  }

  // Step 7: Update velocities and positions (following exact PSO.java updateVelocitiesAndPositions)
  updateVelocitiesAndPositions(population, tasks) {
    for (const particle of population) {
      // Update velocity and position for each dimension
      for (let i = 0; i < tasks.length; i++) {
        const r1 = Math.random();
        const r2 = Math.random();

        // PSO velocity update formula (exact same as PSO.java)
        const vPrev = particle.velocity[i];
        const pBest = particle.personalBest[i];
        const gBest = this.globalBestPosition[i];
        const currentPosition = particle.chromosome[i];

        const newVelocity = this.w * vPrev
          + this.l1 * r1 * (pBest - currentPosition)
          + this.l2 * r2 * (gBest - currentPosition);

        // Apply velocity bounds (following PSO.java logic)
        const Vmax = this.workerCount * 0.5;
        particle.velocity[i] = Math.max(-Vmax, Math.min(Vmax, newVelocity));

        // Update position (following PSO.java logic)
        particle.chromosome[i] = Math.round(currentPosition + particle.velocity[i]);
        particle.chromosome[i] = Math.max(0, Math.min(this.workerCount - 1, particle.chromosome[i]));
      }
    }
  }

  // PSO implementation following exact PSO.java main structure
  run(tasks) {
    console.log("Starting PSO algorithm");
    
    // Step 1: Initialize population with random positions and velocities
    const population = this.initPopulation(tasks.length);
    
    // Step 2: Evaluate initial fitness for all particles
    this.evaluateFitness(population, tasks);
    
    console.log("Initial fitness evaluation completed");
    console.log("Initial Best Fitness: " + this.globalBestFitness);
    
    // Main iteration loop - following exact PSO.java pattern
    let iteration = 1;
    while (iteration <= this.maxIterations) {
      console.log(`\n========== PSO ITERATION ${iteration} ==========`);
      
      // Step 7: Update velocities and positions for all particles
      this.updateVelocitiesAndPositions(population, tasks);
      
      // Step 4-6: Evaluate fitness and update personal/global best
      this.evaluateFitness(population, tasks);
      
      console.log("Iteration " + iteration + " Best Fitness: " + this.globalBestFitness);
      
      iteration++;
    }
    
    console.log("\nPSO ALGORITHM COMPLETED:");
    console.log("Best fitness: " + this.globalBestFitness);
    
    // Ensure we return a valid solution
    if (this.globalBestPosition === null) {
      console.log("Warning: No valid solution found. Returning default solution.");
      this.globalBestPosition = Array(tasks.length).fill(0);
    }

    return this.globalBestPosition;
  }
}

// Main interface function (following abc.js approach exactly)
function runPsoAlgorithm(taskCount, workerCount, tasks, workerMipsArray, useAdvanced = false) {
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
  
  console.log("🐝 Initializing PSO algorithm");
  
  // Create PSO instance
  const pso = new PSOStandard(workerCount, workerMipsArray);
  const solution = pso.run(validatedTasks);
  
  // Return valid solution or default fallback (same as abc.js)
  if (!Array.isArray(solution) || solution.length !== taskCount) {
    return solution?.length < taskCount 
      ? [...solution, ...Array(taskCount - solution.length).fill(0)]
      : solution?.slice(0, taskCount) || Array(taskCount).fill(0);
  }
  
  return solution;
}

module.exports = { 
  runPsoAlgorithm,
  PSOStandard,
  calcFitness,
  createParticle,
  getRandomInt,
  COST_PER_MIPS,
  COST_PER_TASKLENGTH,
  DEFAULT_POPULATION_SIZE,
  DEFAULT_ITERATIONS,
  TASK_LENGTH_CONFIG
};
  