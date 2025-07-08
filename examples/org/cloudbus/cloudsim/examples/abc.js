// Constants for ABC parameters
// C. Controllable Parameters
const DEFAULT_POPULATION_SIZE = 30; // Swarm Size
const DEFAULT_ITERATIONS = 15;
const DEFAULT_LIMIT = 9; // Threshold for abandonment
const DEFAULT_EOBL_COEFFICIENT = 0.9; // d coefficient for EOBL

// Cost constants for ABC optimization (following CloudSim approach)
const COST_PER_MIPS = 0.5;
const COST_PER_TASKLENGTH = 0.1;

// Hardcoded task length values based on task weight (like CloudSim approach)
// Used only for ABC algorithm fitness calculation
const TASK_LENGTH_CONFIG = {
  ringan: 45,    // Light tasks: minimal task length
  sedang: 350,   // Medium tasks: moderate task length  
  berat: 1150    // Heavy tasks: high task length
};

// Cache for fitness calculations to prevent redundant calculations
const fitnessCache = new Map();

// Utility function for random integer generation (from choa.js)
function getRandomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// Step 4: Generate initial population
// The role of employee bee is to collect food from determined food source for the hive
function createIndividual(taskCount, workerCount) {
  const position = Array.from({ length: taskCount }, () => getRandomInt(0, workerCount - 1));
  return {
    position,
    fitness: -1
  };
}

// Initialize population for ABC algorithm
function createInitialPopulation(populationSize, taskCount, workerCount) {
  return Array.from({ length: populationSize }, () => createIndividual(taskCount, workerCount));
}

// Step 5: Evaluate the fitness of individuals
// Cost calculation only for ABC optimization (not final metrics)
function estimateFitness(individual, tasks, workerMipsArray) {
  const position = Array.isArray(individual) ? individual : individual.position;
  const cacheKey = position.join(',');
  if (fitnessCache.has(cacheKey)) {
    const cachedFitness = fitnessCache.get(cacheKey);
    if (!Array.isArray(individual)) individual.fitness = cachedFitness;
    return cachedFitness;
  }
  
  let totalExec = 0;
  let totalCost = 0;
  
  for (let i = 0; i < position.length; i++) {
    const workerIndex = position[i];
    const task = tasks[i];
    let taskLength = task.length || task.mi || task.MI || 1000;
    
    // Use real MIPS from worker (not from task)
    const mips = workerMipsArray[workerIndex] || 500;
    const execTime = taskLength / mips;
    
    // Get task length modifier based on task weight (hardcoded like CloudSim)
    const taskWeight = task.weight || 'sedang';
    const taskLengthModifier = TASK_LENGTH_CONFIG[taskWeight] || TASK_LENGTH_CONFIG.sedang;
    
    // Cost calculation for ABC optimization: execution cost + task length cost
    const computationalCost = execTime * COST_PER_MIPS;
    const taskLengthCost = taskLengthModifier * COST_PER_TASKLENGTH;
    const cost = computationalCost + taskLengthCost;
    
    totalExec += execTime;
    totalCost += cost;
  }
  
  // Fitness calculation: higher fitness = better solution
  const makespanFitness = 1 / totalExec;
  const costFitness = 1 / totalCost;
  const fitness = makespanFitness + costFitness;
  
  fitnessCache.set(cacheKey, fitness);
  if (!Array.isArray(individual)) individual.fitness = fitness;
  return fitness;
}

class ABCStandard {
  constructor(numWorkers, workerMipsArray) {
    // Step 3: Define problem dimension
    this.numWorkers = numWorkers;
    this.workerMipsArray = workerMipsArray;
    
    // C. Controllable Parameters (aligned with ABC.java)
    this.populationSize = DEFAULT_POPULATION_SIZE; // Swarm Size
    this.employedBeeCount = Math.floor(this.populationSize / 2); // 50% of swarm - employed bees
    this.onlookerBeeCount = this.populationSize - this.employedBeeCount; // 50% of swarm - onlooker bees
    this.scoutBeeCount = 1; // Always 1 scout bee
    this.limit = DEFAULT_LIMIT; // Threshold for abandonment
    this.maxIterations = DEFAULT_ITERATIONS; // Imax
    
    // Tracking variables (same as ABC.java)
    this.abandonmentCounter = new Array(this.populationSize).fill(0);
    this.bestSolution = null;
    this.bestFitness = -Infinity;
  }

  // Standard ABC implementation following standard ABC pseudocode EXACTLY
  run(tasks) {
    // 1: begin
    
    // 2: Set iteration t = 1.
    let t = 1;
    console.log("Starting standard ABC algorithm");
    console.log("Iteration counter initialized: " + t);
    
    // 3: Define problem dimension.
    const dimensions = tasks.length;
    console.log("Problem dimension defined: " + dimensions);
    
    // 4: Generate initial population.
    let population = createInitialPopulation(this.populationSize, tasks.length, this.numWorkers);
    console.log("Initial population size: " + population.length);
    
    // 5: Evaluate the fitness of the individuals.
    for (const individual of population) {
      estimateFitness(individual, tasks, this.workerMipsArray);
      
      // Initialize best solution to the first valid solution to avoid null result
      if (this.bestSolution === null || individual.fitness > this.bestFitness) {
        this.bestFitness = individual.fitness;
        this.bestSolution = [...individual.position];
      }
    }
    console.log("Initial fitness evaluation completed");

    // Reset abandonment counters
    this.abandonmentCounter.fill(0);

    // 6: while (termination condition not reached) do
    while (t <= this.maxIterations) {
      console.log(`\n========== ITERATION ${t} ==========`);
      
      // 7: for each employee bees:
      // 8: Find a new food sources and evaluate the fitness.
      // 9: Apply greedy selection mechanism.
      // 10: end for
      console.log("PHASE 1: Employed Bee Phase");
      this.employedBeePhase(population, tasks);
      
      // 11: Calculate the probability for each food source.
      console.log("Calculating selection probabilities based on fitness");
      const probabilities = this.calculateProbabilities(population);
      
      // 12: for each onlooker bees:
      // 13: Chooses a food source
      // 14: Produce new food source
      // 15: Evaluate the fitness.
      // 16: Apply greedy selection mechanism.
      // 17: end for
      console.log("PHASE 2: Onlooker Bee Phase");
      this.onlookerBeePhase(population, probabilities, tasks);
      
      // Store Best Food Source Solution
      this.storeBestFoodSource(population);
      
      // 18: Scout Bee phase
      // 19: if any employed bee becomes scout bee
      // 20: Send the scout bee at a randomly produced food source.
      // 21: end if
      console.log("PHASE 3: Scout Bee Phase");
      const scoutBeeExists = this.checkForScoutBees(population);
      
      if (scoutBeeExists) {
        console.log("Scout bee found - performing scout bee step");
        this.scoutBeePhase(population, tasks);
      } else {
        console.log("No food sources abandoned - no scout bee needed");
      }
      
      // Update best solution
      const currentBest = population.reduce((best, current) => 
        current.fitness > best.fitness ? current : best
      );
      
      if (currentBest.fitness > this.bestFitness) {
        this.bestFitness = currentBest.fitness;
        this.bestSolution = [...currentBest.position];
      }
      
      console.log("Current Best Fitness: " + this.bestFitness);

      // 22: Set iteration t = t + 1.
      t++;
    }
    // 23: end while
    // 24: end

    console.log("\nABC ALGORITHM COMPLETED:");
    console.log("Total iterations: " + (t - 1));
    console.log("Best fitness: " + this.bestFitness);
    
    // Ensure we return a valid solution even if no improvement was found
    if (this.bestSolution === null) {
      console.log("Warning: No valid solution found. Returning default solution.");
      this.bestSolution = Array(tasks.length).fill(0);
    }

    return this.bestSolution;
  }

  // Store Best Food Source Solution (following ABC.java)
  storeBestFoodSource(population) {
    let bestFitness = -Infinity;
    let bestIndex = -1;
    
    for (let i = 0; i < population.length; i++) {
      if (population[i].fitness > bestFitness) {
        bestFitness = population[i].fitness;
        bestIndex = i;
      }
    }
    
    if (bestFitness > this.bestFitness) {
      this.bestFitness = bestFitness;
      this.bestSolution = [...population[bestIndex].position];
      console.log("Updated Best Food Source with fitness: " + bestFitness);
    }
  }

  // Step 7-9: Employee Bee Phase WITHOUT FEs counter for standard ABC
  employedBeePhase(population, tasks) {
    console.log("Employed Bee Phase: Processing " + this.employedBeeCount + " employed bees");
    
    // For each employed bee (following ABC.java structure)
    for (let i = 0; i < this.employedBeeCount; i++) {
      const currentBee = population[i];
      
      // Step 8: Find a new food sources and evaluate the fitness
      // Create new solution by modifying current solution (following ABC.java)
      const newSolution = {
        position: [...currentBee.position],
        fitness: -1
      };
      
      // Choose random dimension to modify (following ABC.java)
      const dimension = Math.floor(Math.random() * currentBee.position.length);
      
      // Choose partner food source (not including current) (following ABC.java)
      let partnerIndex;
      do {
        partnerIndex = Math.floor(Math.random() * this.employedBeeCount);
      } while (partnerIndex === i);
      
      const partner = population[partnerIndex];
      
      // Apply position bounds (following ABC.java logic)
      const minPosition = 0;
      const maxPosition = this.numWorkers - 1;
      
      // Calculate new position using ABC formula: vij = xij + φij(xij - xkj)
      // (following ABC.java implementation)
      const phi = getRandomInt(-1, 1); // Random value between -1, 0, 1 (same as ABC.java)
      let newValue = currentBee.position[dimension] + 
                    phi * (currentBee.position[dimension] - partner.position[dimension]);
      
      // Ensure new position is within bounds (following ABC.java)
      if (newValue < minPosition) {
        newValue = minPosition;
      } else if (newValue > maxPosition) {
        newValue = maxPosition;
      }
      
      newSolution.position[dimension] = newValue;
      
      // Evaluate fitness of new food source (following ABC.java)
      const newFitness = estimateFitness(newSolution, tasks, this.workerMipsArray);

      // Step 9: Apply greedy selection mechanism (following ABC.java)
      if (newFitness > currentBee.fitness) {
        // Replace current solution (Positive Feedback)
        population[i] = newSolution;
        this.abandonmentCounter[i] = 0; // Reset counter on improvement
        
        // Update global best if needed (following ABC.java)
        if (newFitness > this.bestFitness) {
          this.bestFitness = newFitness;
          this.bestSolution = [...newSolution.position];
        }
      } else {
        // Increment abandonment counter (Negative Feedback) (following ABC.java)
        this.abandonmentCounter[i]++;
      }
    }
  }

  // Step 11: Calculate probabilities for onlooker bees
  // Part of Multiple Interactions characteristic - information sharing
  calculateProbabilities(population) {
    const probabilities = new Array(this.populationSize);
    let fitnessSum = 0;
    
    // Sum fitness values for employed bees only (following ABC.java)
    for (let i = 0; i < this.employedBeeCount; i++) {
      fitnessSum += population[i].fitness;
    }
    
    // Calculate probabilities for each food source (following ABC.java)
    for (let i = 0; i < this.populationSize; i++) {
      if (i < this.employedBeeCount && fitnessSum > 0) {
        probabilities[i] = population[i].fitness / fitnessSum;
      } else {
        probabilities[i] = 0; // Non-employed bees get zero probability
      }
    }
    
    return probabilities;
  }

  // Helper method to select food source based on probabilities (following ABC.java)
  selectFoodSource(probabilities) {
    const r = Math.random();
    let sum = 0;
    
    // Roulette wheel selection (following ABC.java)
    for (let i = 0; i < this.employedBeeCount; i++) {
      sum += probabilities[i];
      if (r <= sum) {
        return i;
      }
    }
    
    // Fallback - return random employed bee (following ABC.java)
    return Math.floor(Math.random() * this.employedBeeCount);
  }

  // Step 12-17: Onlooker Bee Phase WITHOUT FEs counter for standard ABC
  onlookerBeePhase(population, probabilities, tasks) {
    console.log("Onlooker Bee Phase: Processing " + this.onlookerBeeCount + " onlooker bees");
    
    // Process onlooker bees (following ABC.java structure)
    let onlookerCount = 0;
    
    while (onlookerCount < this.onlookerBeeCount) {
      // Step 13: Choose food source based on quality (probability) (following ABC.java)
      const selectedFoodSource = this.selectFoodSource(probabilities);
      
      // Step 14: Produce new food source (following ABC.java)
      const selectedBee = population[selectedFoodSource];
      const newSolution = {
        position: [...selectedBee.position],
        fitness: -1
      };

      // Choose random dimension to modify (following ABC.java)
      const dimension = Math.floor(Math.random() * selectedBee.position.length);
      
      // Choose partner food source (not including current) (following ABC.java)
      let partnerIndex;
      do {
        partnerIndex = Math.floor(Math.random() * this.employedBeeCount);
      } while (partnerIndex === selectedFoodSource);
      
      const partner = population[partnerIndex];
      
      // Apply position bounds (following ABC.java logic)
      const minPosition = 0;
      const maxPosition = this.numWorkers - 1;
      
      // Calculate new position using formula: vij = xij + φij(xij - xkj) (following ABC.java)
      const phi = getRandomInt(-1, 1); // Random value between -1, 0, 1 (same as ABC.java)
      let newValue = selectedBee.position[dimension] + 
                    phi * (selectedBee.position[dimension] - partner.position[dimension]);
      
      // Ensure new position is within bounds (following ABC.java)
      if (newValue < minPosition) {
        newValue = minPosition;
      } else if (newValue > maxPosition) {
        newValue = maxPosition;
      }
      
      newSolution.position[dimension] = newValue;

      // Step 15: Evaluate fitness
      const newFitness = estimateFitness(newSolution, tasks, this.workerMipsArray);

      // Step 16: Apply greedy selection mechanism (following ABC.java)
      if (newFitness > selectedBee.fitness) {
        // Replace selected food source (Positive Feedback) (following ABC.java)
        population[selectedFoodSource] = newSolution;
        this.abandonmentCounter[selectedFoodSource] = 0; // Reset counter
        
        // Update global best if needed (following ABC.java)
        if (newFitness > this.bestFitness) {
          this.bestFitness = newFitness;
          this.bestSolution = [...newSolution.position];
        }
      } else {
        // Increment abandonment counter (Negative Feedback) (following ABC.java)
        this.abandonmentCounter[selectedFoodSource]++;
      }
      
      onlookerCount++;
    }
  }

  // Step 18-21: Scout Bee Phase WITHOUT FEs counter for standard ABC
  scoutBeePhase(population, tasks) {
    // Find most abandoned food source (following ABC.java logic)
    let maxAbandonmentIndex = -1;
    let maxAbandonmentCount = -1;
    
    for (let i = 0; i < this.employedBeeCount; i++) {
      if (this.abandonmentCounter[i] > maxAbandonmentCount) {
        maxAbandonmentCount = this.abandonmentCounter[i];
        maxAbandonmentIndex = i;
      }
    }
    
    // Step 20: Send scout bee to random food source if limit exceeded (following ABC.java)
    if (maxAbandonmentCount > this.limit && maxAbandonmentIndex >= 0) {
      console.log("Food source at position " + maxAbandonmentIndex + 
                  " abandoned after " + maxAbandonmentCount + " trials (limit: " + this.limit + ")");
      console.log("Scout bee is exploring for new food source");
      
      // Create completely new random solution (Scout bee explores new area) (following ABC.java)
      const scout = createIndividual(tasks.length, this.numWorkers);
      const fitness = estimateFitness(scout, tasks, this.workerMipsArray);
      
      // Replace abandoned solution - Fluctuations characteristic (following ABC.java)
      population[maxAbandonmentIndex] = scout;
      this.abandonmentCounter[maxAbandonmentIndex] = 0;
      
      console.log("Scout bee found new food source with fitness: " + fitness);

      // Update global best if needed - Multiple Interactions (following ABC.java)
      if (fitness > this.bestFitness) {
        this.bestFitness = fitness;
        this.bestSolution = [...scout.position];
        console.log("New food source is better than current best - information shared with colony");
      }
    } else {
      console.log("No food sources abandoned this iteration");
    }
  }

  // Check for scout bees (following ABC.java)
  checkForScoutBees(population) {
    for (let i = 0; i < this.employedBeeCount; i++) {
      if (this.abandonmentCounter[i] > this.limit) {
        return true;
      }
    }
    return false;
  }
}

class ABCEOBL {
  constructor(numWorkers, workerMipsArray) {
    // Step 3: Define problem dimension
    this.numWorkers = numWorkers;
    this.workerMipsArray = workerMipsArray;
    
    // C. Controllable Parameters (aligned with ABC.java)
    this.populationSize = DEFAULT_POPULATION_SIZE; // Swarm Size (SN in EOBL pseudocode)
    this.employedBeeCount = Math.floor(this.populationSize / 2); // 50% of swarm - employed bees
    this.onlookerBeeCount = this.populationSize - this.employedBeeCount; // 50% of swarm - onlooker bees
    this.scoutBeeCount = 1; // Always 1 scout bee
    this.limit = DEFAULT_LIMIT; // Threshold for abandonment
    this.maxIterations = DEFAULT_ITERATIONS; // Imax
    
    // EOBL parameters (aligned with ABC.java)
    this.d = DEFAULT_EOBL_COEFFICIENT; // Elite coefficient (same as ABC.java)
    
    // Tracking variables (same as ABC.java)
    this.abandonmentCounter = new Array(this.populationSize).fill(0);
    this.bestSolution = null;
    this.bestFitness = -Infinity;
    
    // FEs counter (following ABC.java implementation)
    this.FEs = 0;
  }

  // Helper method to select elite solutions (following ABC.java)
  selectEliteSolutions(population, eliteCount) {
    // Sort individuals by fitness in descending order
    const sortedPopulation = [...population].sort((a, b) => b.fitness - a.fitness);
    return sortedPopulation.slice(0, eliteCount);
  }

  // Helper method to create elite opposition-based solution (following ABC.java)
  createEliteOpposition(original, eliteSolutions, minPosition, maxPosition, k) {
    const newSolution = {
      position: [...original.position],
      fitness: -1
    };

    // Select elite solution based on random k (following ABC.java logic)
    let eliteIndex = Math.floor(k * eliteSolutions.length);
    if (eliteIndex >= eliteSolutions.length) {
      eliteIndex = eliteSolutions.length - 1;
    }
    const eliteSolution = eliteSolutions[eliteIndex];

    // Apply elite opposition for each dimension (following ABC.java formula)
    for (let i = 0; i < original.position.length; i++) {
      // Formula from ABC.java: opposite = (minPosition + maxPosition) - original
      const opposite = (minPosition + maxPosition) - original.position[i];
      
      // Combine with elite solution using coefficient d (following ABC.java)
      const newValue = Math.floor((1 - this.d) * opposite + this.d * eliteSolution.position[i]);
      
      // Ensure value is within bounds
      newSolution.position[i] = Math.max(minPosition, Math.min(maxPosition, newValue));
    }

    return newSolution;
  }

  // Helper method to merge and select best solutions (following ABC.java)
  mergeAndSelectBest(population, oppositionPopulation) {
    // Combine both populations
    const combined = [...population, ...oppositionPopulation];
    
    // Sort by fitness in descending order
    combined.sort((a, b) => b.fitness - a.fitness);
    
    // Take the best solutions up to population size
    const selected = combined.slice(0, this.populationSize);
    
    // Update original population with selected individuals
    for (let i = 0; i < this.populationSize; i++) {
      population[i] = selected[i];
    }
  }

  // EOBL implementation following ABC.java runABCEOBL method and STRICT pseudocode adherence
  run(tasks) {
    // Following EOBL pseudocode exactly:
    // t = 0;
    let t = 0;
    console.log("Starting EOABC algorithm");
    console.log("Iteration counter initialized: " + t);
    
    // FEs = 0;
    this.FEs = 0;
    console.log("Function evaluation counter initialized: " + this.FEs);
    
    // Initialize the population;
    let population = createInitialPopulation(this.populationSize, tasks.length, this.numWorkers);
    console.log("Initial population size: " + population.length);
    
    // Evaluate initial population fitness
    for (const individual of population) {
      estimateFitness(individual, tasks, this.workerMipsArray);
      this.FEs++;
      
      // Initialize best solution to the first valid solution to avoid null result
      if (this.bestSolution === null || individual.fitness > this.bestFitness) {
        this.bestFitness = individual.fitness;
        this.bestSolution = [...individual.position];
      }
    }
    console.log("Initial fitness evaluation completed, FEs = " + this.FEs);

    // Reset abandonment counters
    this.abandonmentCounter.fill(0);

    // Calculate maximum function evaluations (following ABC.java)
    const MAX_FEs = this.maxIterations * this.populationSize;
    console.log("Maximum function evaluations: " + MAX_FEs);
    
    // while FEs < MAX_FEs do
    while (this.FEs < MAX_FEs) {
      console.log(`\n========== ITERATION ${t + 1} ==========`);
      console.log(`Function evaluations: ${this.FEs}/${MAX_FEs}`);

      // Pr = rand(0, 1);
      const Pr = Math.random();
      const Pe = 0.5; // Probability threshold for EOBL (same as ABC.java)
      console.log("Random probability Pr = " + Pr);

      try {
        // if Pr < Pe then
        if (Pr < Pe) {
          console.log("Using EOBL procedure - Pr < Pe");
          
          // Choose EN elite solutions from the current population;
          const EN = Math.max(2, Math.floor(this.populationSize / 10)); // 10% of population
          const eliteSolutions = this.selectEliteSolutions(population, EN);
          console.log("Selected " + EN + " elite solutions as guides for opposition");
          
          // Calculate the lower and upper boundaries of the chosen elite solutions;
          const minPosition = 0;
          const maxPosition = this.numWorkers - 1;
          console.log("Search space boundaries: [" + minPosition + ", " + maxPosition + "]");
          
          // EOP = {};
          const oppositionPopulation = [];
          
          // for i = 1 to SN do
          console.log("Generating elite opposition-based solutions for each individual...");
          for (let i = 0; i < this.populationSize; i++) {
            // k = rand(0, 1);
            const k = Math.random();
            
            // Create the elite opposition-based solution EOi for the ith solution Xi;
            const oppositeIndividual = this.createEliteOpposition(
              population[i], 
              eliteSolutions, 
              minPosition, 
              maxPosition, 
              k
            );
            
            // Evaluate solution EOi;
            estimateFitness(oppositeIndividual, tasks, this.workerMipsArray);
            
            // EOP = EOP ∪ {EOi};
            oppositionPopulation.push(oppositeIndividual);
            
            // FEs = FEs + 1;
            this.FEs++;
          }
          // end for
          
          // Choose the top best SN solutions from {P, EOP} for the next generation population;
          console.log("Merging populations and selecting best individuals...");
          this.mergeAndSelectBest(population, oppositionPopulation);
          
        } else {
          // else Execute the computation procedure of the traditional ABC;
          console.log("Using traditional ABC procedure - Pr >= Pe");
          this.runABCTraditionalWithFEs(population, tasks);
        }
        // end if

        // Update best solution
        const currentBest = population.reduce((best, current) => 
          current.fitness > best.fitness ? current : best
        );
        
        if (currentBest.fitness > this.bestFitness) {
          this.bestFitness = currentBest.fitness;
          this.bestSolution = [...currentBest.position];
        }
        
        console.log("Current Best Fitness: " + this.bestFitness);
        
      } catch (error) {
        console.error(`Error in iteration ${t + 1}:`, error);
      }

      // t = t + 1;
      t++;
    }
    // end while

    console.log("\nEOABC ALGORITHM COMPLETED:");
    console.log("Total iterations:", t);
    console.log("Total function evaluations:", this.FEs);
    console.log("Best fitness:", this.bestFitness);
    
    // Ensure we return a valid solution even if no improvement was found
    if (this.bestSolution === null) {
      console.log("Warning: No valid solution found. Returning default solution.");
      this.bestSolution = Array(tasks.length).fill(0);
    }

    return this.bestSolution;
  }

  // Traditional ABC procedure with FEs counter for EOBL (following ABC.java structure)
  runABCTraditionalWithFEs(population, tasks) {
    console.log("- Starting Employed Bee Phase");
    // Step 7-9: for each employee bees
    this.employedBeePhaseWithFEs(population, tasks);
    
    // Step 11: Calculate the probability for each food source
    const probabilities = this.calculateProbabilities(population);
    
    console.log("- Starting Onlooker Bee Phase");
    // Step 12-17: for each onlooker bees
    this.onlookerBeePhaseWithFEs(population, probabilities, tasks);
    
    // Store Best Food Source (following ABC.java)
    this.storeBestFoodSource(population);
    
    console.log("- Checking for Scout Bees");
    // Step 18-21: Scout Bee phase
    const scoutBeeExists = this.checkForScoutBees(population);
    if (scoutBeeExists) {
      console.log("- Scout Bee Found - Starting Scout Bee Phase");
      this.scoutBeePhaseWithFEs(population, tasks);
    } else {
      console.log("- No Scout Bees Found");
    }
    
    console.log("- ABC Procedure Completed Successfully");
  }

  // Store Best Food Source Solution (following ABC.java)
  storeBestFoodSource(population) {
    let bestFitness = -Infinity;
    let bestIndex = -1;
    
    for (let i = 0; i < population.length; i++) {
      if (population[i].fitness > bestFitness) {
        bestFitness = population[i].fitness;
        bestIndex = i;
      }
    }
    
    if (bestFitness > this.bestFitness) {
      this.bestFitness = bestFitness;
      this.bestSolution = [...population[bestIndex].position];
      console.log("Updated Best Food Source with fitness: " + bestFitness);
    }
  }

  // Step 7-9: Employee Bee Phase WITH FEs counter for EOBL
  employedBeePhaseWithFEs(population, tasks) {
    console.log("Employed Bee Phase: Processing " + this.employedBeeCount + " employed bees");
    
    // For each employed bee (following ABC.java structure)
    for (let i = 0; i < this.employedBeeCount; i++) {
      const currentBee = population[i];
      
      // Step 8: Find a new food sources and evaluate the fitness
      // Create new solution by modifying current solution (following ABC.java)
      const newSolution = {
        position: [...currentBee.position],
        fitness: -1
      };
      
      // Choose random dimension to modify (following ABC.java)
      const dimension = Math.floor(Math.random() * currentBee.position.length);
      
      // Choose partner food source (not including current) (following ABC.java)
      let partnerIndex;
      do {
        partnerIndex = Math.floor(Math.random() * this.employedBeeCount);
      } while (partnerIndex === i);
      
      const partner = population[partnerIndex];
      
      // Apply position bounds (following ABC.java logic)
      const minPosition = 0;
      const maxPosition = this.numWorkers - 1;
      
      // Calculate new position using ABC formula: vij = xij + φij(xij - xkj)
      // (following ABC.java implementation)
      const phi = getRandomInt(-1, 1); // Random value between -1, 0, 1 (same as ABC.java)
      let newValue = currentBee.position[dimension] + 
                    phi * (currentBee.position[dimension] - partner.position[dimension]);
      
      // Ensure new position is within bounds (following ABC.java)
      if (newValue < minPosition) {
        newValue = minPosition;
      } else if (newValue > maxPosition) {
        newValue = maxPosition;
      }
      
      newSolution.position[dimension] = newValue;

      // Evaluate fitness of new food source (following ABC.java)
      const newFitness = estimateFitness(newSolution, tasks, this.workerMipsArray);
      this.FEs++; // Update FEs counter for EOBL

      // Step 9: Apply greedy selection mechanism (following ABC.java)
      if (newFitness > currentBee.fitness) {
        // Replace current solution (Positive Feedback)
        population[i] = newSolution;
        this.abandonmentCounter[i] = 0; // Reset counter on improvement
        
        // Update global best if needed (following ABC.java)
        if (newFitness > this.bestFitness) {
          this.bestFitness = newFitness;
          this.bestSolution = [...newSolution.position];
        }
      } else {
        // Increment abandonment counter (Negative Feedback) (following ABC.java)
        this.abandonmentCounter[i]++;
      }
    }
  }

  // Step 11: Calculate probabilities for onlooker bees
  // Part of Multiple Interactions characteristic - information sharing
  calculateProbabilities(population) {
    const probabilities = new Array(this.populationSize);
    let fitnessSum = 0;
    
    // Sum fitness values for employed bees only (following ABC.java)
    for (let i = 0; i < this.employedBeeCount; i++) {
      fitnessSum += population[i].fitness;
    }
    
    // Calculate probabilities for each food source (following ABC.java)
    for (let i = 0; i < this.populationSize; i++) {
      if (i < this.employedBeeCount && fitnessSum > 0) {
        probabilities[i] = population[i].fitness / fitnessSum;
      } else {
        probabilities[i] = 0; // Non-employed bees get zero probability
      }
    }
    
    return probabilities;
  }

  // Helper method to select food source based on probabilities (following ABC.java)
  selectFoodSource(probabilities) {
    const r = Math.random();
    let sum = 0;
    
    // Roulette wheel selection (following ABC.java)
    for (let i = 0; i < this.employedBeeCount; i++) {
      sum += probabilities[i];
      if (r <= sum) {
        return i;
      }
    }
    
    // Fallback - return random employed bee (following ABC.java)
    return Math.floor(Math.random() * this.employedBeeCount);
  }

  // Step 12-17: Onlooker Bee Phase WITH FEs counter for EOBL
  onlookerBeePhaseWithFEs(population, probabilities, tasks) {
    console.log("Onlooker Bee Phase: Processing " + this.onlookerBeeCount + " onlooker bees");
    
    // Process onlooker bees (following ABC.java structure)
    let onlookerCount = 0;
    
    while (onlookerCount < this.onlookerBeeCount) {
      // Step 13: Choose food source based on quality (probability) (following ABC.java)
      const selectedFoodSource = this.selectFoodSource(probabilities);
      
      // Step 14: Produce new food source (following ABC.java)
      const selectedBee = population[selectedFoodSource];
        const newSolution = {
        position: [...selectedBee.position],
          fitness: -1
        };

      // Choose random dimension to modify (following ABC.java)
      const dimension = Math.floor(Math.random() * selectedBee.position.length);
      
      // Choose partner food source (not including current) (following ABC.java)
        let partnerIndex;
        do {
          partnerIndex = Math.floor(Math.random() * this.employedBeeCount);
      } while (partnerIndex === selectedFoodSource);
        
        const partner = population[partnerIndex];
        
      // Apply position bounds (following ABC.java logic)
      const minPosition = 0;
      const maxPosition = this.numWorkers - 1;
      
      // Calculate new position using formula: vij = xij + φij(xij - xkj) (following ABC.java)
      const phi = getRandomInt(-1, 1); // Random value between -1, 0, 1 (same as ABC.java)
      let newValue = selectedBee.position[dimension] + 
                    phi * (selectedBee.position[dimension] - partner.position[dimension]);
      
      // Ensure new position is within bounds (following ABC.java)
      if (newValue < minPosition) {
        newValue = minPosition;
      } else if (newValue > maxPosition) {
        newValue = maxPosition;
      }
      
        newSolution.position[dimension] = newValue;

        // Step 15: Evaluate fitness
      const newFitness = estimateFitness(newSolution, tasks, this.workerMipsArray);
      this.FEs++; // Update FEs counter for EOBL

      // Step 16: Apply greedy selection mechanism (following ABC.java)
      if (newFitness > selectedBee.fitness) {
        // Replace selected food source (Positive Feedback) (following ABC.java)
        population[selectedFoodSource] = newSolution;
        this.abandonmentCounter[selectedFoodSource] = 0; // Reset counter
        
        // Update global best if needed (following ABC.java)
          if (newFitness > this.bestFitness) {
            this.bestFitness = newFitness;
            this.bestSolution = [...newSolution.position];
          }
        } else {
        // Increment abandonment counter (Negative Feedback) (following ABC.java)
        this.abandonmentCounter[selectedFoodSource]++;
      }
      
      onlookerCount++;
    }
  }

  // Step 18-21: Scout Bee Phase WITH FEs counter for EOBL
  scoutBeePhaseWithFEs(population, tasks) {
    // Find most abandoned food source (following ABC.java logic)
    let maxAbandonmentIndex = -1;
    let maxAbandonmentCount = -1;
    
    for (let i = 0; i < this.employedBeeCount; i++) {
      if (this.abandonmentCounter[i] > maxAbandonmentCount) {
        maxAbandonmentCount = this.abandonmentCounter[i];
        maxAbandonmentIndex = i;
      }
    }
    
    // Step 20: Send scout bee to random food source if limit exceeded (following ABC.java)
    if (maxAbandonmentCount > this.limit && maxAbandonmentIndex >= 0) {
      console.log("Food source at position " + maxAbandonmentIndex + 
                  " abandoned after " + maxAbandonmentCount + " trials (limit: " + this.limit + ")");
      console.log("Scout bee is exploring for new food source");
      
      // Create completely new random solution (Scout bee explores new area) (following ABC.java)
      const scout = createIndividual(tasks.length, this.numWorkers);
      const fitness = estimateFitness(scout, tasks, this.workerMipsArray);
      this.FEs++; // Update FEs counter for EOBL
      
      // Replace abandoned solution - Fluctuations characteristic (following ABC.java)
      population[maxAbandonmentIndex] = scout;
      this.abandonmentCounter[maxAbandonmentIndex] = 0;
      
      console.log("Scout bee found new food source with fitness: " + fitness);

      // Update global best if needed - Multiple Interactions (following ABC.java)
        if (fitness > this.bestFitness) {
          this.bestFitness = fitness;
          this.bestSolution = [...scout.position];
        console.log("New food source is better than current best - information shared with colony");
      }
    } else {
      console.log("No food sources abandoned this iteration");
    }
  }

  // Check for scout bees (following ABC.java)
  checkForScoutBees(population) {
    for (let i = 0; i < this.employedBeeCount; i++) {
      if (this.abandonmentCounter[i] > this.limit) {
        return true;
      }
    }
    return false;
  }
}

function runABCAlgorithm(taskCount, numWorkers, tasks, workerMipsArray, useEOBL) {
  // Basic input validation
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return Array(taskCount).fill(0);
  }
  
  if (!Array.isArray(workerMipsArray) || workerMipsArray.length !== numWorkers) {
    return Array(taskCount).fill(0);
  }
  
  // Simplified task processing - just ensure tasks have length property
  const validatedTasks = tasks.map((task, i) => ({
    name: task?.name || `Task-${i}`,
    length: task?.length || task?.mi || task?.MI || 1000,
    weight: task?.weight || 'sedang'
  }));
  
  // Run ABC algorithm with the specified EOBL configuration
  let abc;
  if (useEOBL) {
    console.log("🐝 Initializing ABC + EOBL algorithm");
    abc = new ABCEOBL(numWorkers, workerMipsArray);
    } else {
    console.log("🐝 Initializing Standard ABC algorithm");
    abc = new ABCStandard(numWorkers, workerMipsArray);
  }
  
  const solution = abc.run(validatedTasks);
  
  // Return valid solution or default fallback
  if (!Array.isArray(solution) || solution.length !== taskCount) {
    return solution?.length < taskCount 
      ? [...solution, ...Array(taskCount - solution.length).fill(0)]
      : solution?.slice(0, taskCount) || Array(taskCount).fill(0);
  }
  
  return solution;
}

module.exports = { 
  runABCAlgorithm,
  ABCStandard,
  ABCEOBL,
  estimateFitness,
  createIndividual,
  createInitialPopulation,
  getRandomInt, // Export utility function
  COST_PER_MIPS,
  COST_PER_TASKLENGTH,
  DEFAULT_POPULATION_SIZE,
  DEFAULT_ITERATIONS,
  DEFAULT_LIMIT,
  TASK_LENGTH_CONFIG
};