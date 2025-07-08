const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
const { runABCAlgorithm } = require('./abc_algorithm/abc');

const app = express();
app.use(express.json());

// Configuration flags for algorithm behavior
// Set to false to use standard ABC (should give better makespan but worse imbalance)
// Set to true to use EOBL (should give worse makespan but better imbalance)
const USE_EOBL = true; // Change this to false to test standard ABC vs EOBL

const workers = [
  'http://192.168.56.11:31001',
  'http://192.168.56.11:31002',
  'http://192.168.56.12:31001',
  'http://192.168.56.12:31002',
  'http://192.168.56.13:31001',
  'http://192.168.56.13:31002'
];

let simulationStart = null;
let simulationEnd = null;
let completedTasks = 0;
const totalTasks = 1000;
let batchIndex = 0;
let totalCost = 0;

// Arrays to store execution data for each worker (to simulate parallel execution)
const workerExecutionData = {};
const cpuUsages = []; 
let workerFinishTimes = {}; // Track when each worker finishes all assigned tasks

let tasks = [];
let abcMapping = [];
let allTaskResults = []; // Store individual task results for metrics calculation
let workerMipsArray = []; // Array MIPS yang didapat sekali dari workers

try {
  const data = fs.readFileSync(path.join(__dirname, 'tasks1000.json'));
  tasks = JSON.parse(data);
} catch (err) {
  console.error('Gagal membaca tasks.json:', err.message);
  process.exit(1);
}

// Initialize worker execution data
workers.forEach(worker => {
  workerExecutionData[worker] = {
    tasks: [],
    totalExecutionTime: 0,
    startTime: null,
    endTime: null
  };
  workerFinishTimes[worker] = 0;
});

// Function to get real MIPS directly from workers via API
async function getRealWorkerMIPS() {
  const realMipsArray = [];
  const failedWorkers = [];
  
  // Query MIPS dari setiap worker langsung via API
  for (let i = 0; i < workers.length; i++) {
    const worker = workers[i];
    
    // Coba beberapa port berbeda untuk setiap worker
    const possiblePorts = [31001 + (i % 2), 3000]; // 31001/31002 atau fallback ke 3000
    const baseUrl = worker.split(':').slice(0, 2).join(':'); // http://IP
    
    let workerMips = null;
    let success = false;
    
    for (const port of possiblePorts) {
      const testUrl = `${baseUrl}:${port}`;
      try {
        const response = await axios.get(`${testUrl}/api/mips`, { 
          timeout: 3000,
          headers: {
            'Content-Type': 'application/json'
          }
        });
        
        if (response.data && typeof response.data.mips === 'number') {
          workerMips = response.data.mips;
          console.log(`✅ Retrieved MIPS from ${testUrl}: ${workerMips.toFixed(6)}`);
          success = true;
          break;
        }
      } catch (err) {
        // Silent fallback to next port
      }
    }
    
    if (!success) {
      console.error(`❌ CRITICAL: Could not get MIPS from worker ${i+1} (${worker})`);
      failedWorkers.push({workerIndex: i+1, worker: worker});
    } else {
      realMipsArray.push(workerMips);
    }
  }
  
  // If any worker failed to provide MIPS, throw error
  if (failedWorkers.length > 0) {
    const errorMessage = `Failed to retrieve MIPS from ${failedWorkers.length} worker(s): ${failedWorkers.map(f => `Worker ${f.workerIndex} (${f.worker})`).join(', ')}`;
    console.error(`❌ ${errorMessage}`);
    console.error('❌ Cannot proceed without real MIPS data from all workers');
    throw new Error(errorMessage);
  }
  
  console.log('📊 Final MIPS array:', realMipsArray);
  return realMipsArray;
}

app.post('/cpu-usage-report', (req, res) => {
  const { host, avgCpu, totalCpuTime, activeContainers, timestamp } = req.body;
  cpuUsages.push({ 
    time: timestamp || Date.now(), 
    host, 
    avgCpu,
    totalCpuTime: totalCpuTime || 0,
    activeContainers: activeContainers || 0
  });
  
  res.json({ status: 'received' });
});

app.post('/schedule', async (req, res) => {
  if (batchIndex === 0) {
    cpuUsages.length = 0;
  }

  if (abcMapping.length === 0) {
    try {
      console.log('🔧 Querying MIPS data directly from workers...');
      
      // Get MIPS sekali saja dari workers
      workerMipsArray = await getRealWorkerMIPS();
      console.log('🔧 Using real MIPS values:', workerMipsArray);
      
      // Run ABC algorithm with the real MIPS configuration
      abcMapping = runABCAlgorithm(tasks.length, workers.length, tasks, workerMipsArray, USE_EOBL);
      console.log('📌 ABC Mapping completed:', abcMapping);

      if (!Array.isArray(abcMapping) || abcMapping.length !== tasks.length) {
        console.error(`❌ Invalid ABC mapping`);
        process.exit(1);
      }

      // Pre-assign all tasks to workers based on ABC mapping
      tasks.forEach((task, index) => {
        const workerIndex = abcMapping[index];
        const targetWorker = workers[workerIndex];
        workerExecutionData[targetWorker].tasks.push({
          ...task,
          originalIndex: index
        });
      });

      console.log('✅ ABC mapping and task pre-assignment completed (NOT counted in makespan)');


    } catch (err) {
      console.error('❌ CRITICAL ERROR: Failed to get MIPS data from workers:', err.message);
      console.error('❌ Application cannot proceed without real MIPS data');
      
      // Return error and terminate if MIPS cannot be retrieved
      return res.status(500).json({ 
        error: 'CRITICAL: Cannot retrieve MIPS data from workers',
        details: err.message,
        action: 'Please ensure all workers are running and accessible'
      });
    }
  }

  // Execute batch of tasks (matching CloudSim batch structure)
  // Simulation: 54 cloudlets per batch (6 datacenter × 9 cloudlets)
  // Real environment: Scale down to 6 tasks per batch (3 hosts × 2 workers) 
  const WORKERS_PER_HOST = 2; // 2 workers per host (scaled from 9 VMs per datacenter)
  const NUM_HOSTS = 3; // 3 hosts (scaled from 6 datacenters)  
  const BATCH_SIZE = NUM_HOSTS * WORKERS_PER_HOST; // 6 tasks per batch (scaled from 54)
  
  const startBatchIndex = batchIndex * BATCH_SIZE;
  const endBatchIndex = Math.min(startBatchIndex + BATCH_SIZE, tasks.length);

  if (startBatchIndex >= tasks.length) {
    return res.status(400).json({ error: 'Semua task telah selesai dijalankan' });
  }

  // Group workers by host (like datacenter structure in simulation)
  const hostWorkers = {
    'host1': ['http://192.168.56.11:31001', 'http://192.168.56.11:31002'],
    'host2': ['http://192.168.56.12:31001', 'http://192.168.56.12:31002'], 
    'host3': ['http://192.168.56.13:31001', 'http://192.168.56.13:31002']
  };

  // Execute tasks in parallel batches per host (like datacenter processing in simulation)
  const batchPromises = [];
  const currentBatchTasks = [];
  
  let currentTaskIndex = startBatchIndex;
  
  // Process each host (like each datacenter in simulation)
  for (const [hostName, hostWorkerList] of Object.entries(hostWorkers)) {
    
    // Process WORKERS_PER_HOST tasks for this host (like 9 cloudlets per datacenter)
    for (let taskInHost = 0; taskInHost < WORKERS_PER_HOST && currentTaskIndex < endBatchIndex; taskInHost++) {
      const task = tasks[currentTaskIndex];
      const workerIndex = abcMapping[currentTaskIndex];
      const targetWorker = workers[workerIndex];
      
      currentBatchTasks.push({
        task,
        taskIndex: currentTaskIndex,
        worker: targetWorker,
        workerIndex,
        host: hostName
      });
      
      currentTaskIndex++;
    }
  }
  
  // Record simulation start time ONLY when first task execution begins (excludes mapping time)
  if (!simulationStart) {
    simulationStart = Date.now();
    console.log('🚀 Task execution started (excluding mapping time)');
  }
  
  for (const batchTask of currentBatchTasks) {
    // Execute task on assigned worker
    const taskSubmissionTime = Date.now(); // Record individual task submission time
    const promise = axios.post(`${batchTask.worker}/api/execute`, { task: batchTask.task.weight })
      .then(response => {
        const execTime = response.data?.result?.execution_time || 0;
        const startTime = response.data?.result?.start_time || 0;
        const finishTime = response.data?.result?.finish_time || 0;
        
        // Calculate waiting time like CloudSim: startTime - submissionTime
        // Ensure non-negative waiting time (handle race conditions)
        const waitingTime = Math.max(0, startTime - taskSubmissionTime);
        
        return {
          taskIndex: batchTask.taskIndex,
          worker: batchTask.worker,
          workerIndex: batchTask.workerIndex,
          execTime,
          startTime,
          finishTime,
          submissionTime: taskSubmissionTime,
          waitingTime: waitingTime,
          task: batchTask.task,
          host: batchTask.host
        };
      })
      .catch(err => {
        return {
          taskIndex: batchTask.taskIndex,
          worker: batchTask.worker,
          workerIndex: batchTask.workerIndex,
          execTime: 1000, // Default execution time on error
          startTime: Date.now(),
          finishTime: Date.now() + 1000,
          submissionTime: taskSubmissionTime,
          waitingTime: Math.max(0, Date.now() - taskSubmissionTime),
          task: batchTask.task,
          host: batchTask.host,
          error: true
        };
      });
    
    batchPromises.push(promise);
  }

  try {
    // Wait for all tasks in this batch to complete (parallel execution)
    const batchResults = await Promise.all(batchPromises);
    
    // Process batch results
    batchResults.forEach(result => {
      // No cost calculation here - handled in ABC algorithm only
      
      // Store individual task result for metrics calculation
      allTaskResults.push(result);
      
      // Update worker execution data
      if (!workerExecutionData[result.worker].startTime) {
        workerExecutionData[result.worker].startTime = result.startTime;
      }
      workerExecutionData[result.worker].totalExecutionTime += result.execTime;
      workerExecutionData[result.worker].endTime = result.finishTime;
      
      // Update worker finish time (cumulative)
      workerFinishTimes[result.worker] = Math.max(
        workerFinishTimes[result.worker], 
        result.finishTime - simulationStart
      );
      
      completedTasks++;
    });

         batchIndex++;
     
     // Show progress every batch or every 100 tasks
     if (completedTasks % BATCH_SIZE === 0 || completedTasks % 100 === 0 || completedTasks === totalTasks) {
       console.log(`📈 Progress: ${completedTasks}/${totalTasks} tasks completed (Batch ${batchIndex}: ${BATCH_SIZE} tasks processed)`);
     }

    // Check if all tasks are completed
    if (completedTasks >= totalTasks) {
      simulationEnd = Date.now();
      
      // Calculate metrics like simulation (parallel execution model)
      // NOTE: simulationStart now excludes ABC mapping time - only pure task execution time
      const simulationDurationMs = simulationEnd - simulationStart;
      const simulationDurationSec = simulationDurationMs / 1000;
      
      // Makespan = maximum finish time among all workers (pure execution time, excludes mapping)
      const workerFinishTimesMs = Object.values(workerFinishTimes);
      const makespanMs = Math.max(...workerFinishTimesMs); // Keep in milliseconds like CloudSim
      
      const throughput = totalTasks / (makespanMs / 1000); // Tasks per second based on makespan
      
      // Calculate execution times for each task (like CloudSim response_time array)
      const taskExecutionTimes = allTaskResults.map(result => result.execTime).filter(time => time > 0);
      
      if (taskExecutionTimes.length === 0) {
        return res.status(500).json({ error: 'No valid execution times found' });
      }
      
      // Calculate imbalance degree based on individual task execution times (like CloudSim)
      const maxResponseTime = Math.max(...taskExecutionTimes);
      const minResponseTime = Math.min(...taskExecutionTimes);
      const avgResponseTime = taskExecutionTimes.reduce((a, b) => a + b, 0) / taskExecutionTimes.length;
      const imbalanceDegree = (maxResponseTime - minResponseTime) / avgResponseTime;
      
      // Calculate total execution time from all tasks
      const totalExecutionTime = taskExecutionTimes.reduce((a, b) => a + b, 0);
      const avgExecutionTime = totalExecutionTime / completedTasks;
      
      // Resource Utilization: Multiple approaches for comprehensive analysis
      
      // 1. Real CPU Usage (Percentage-based like before)
      const grouped = {};
      cpuUsages.forEach(entry => {
        if (!grouped[entry.host]) grouped[entry.host] = [];
        grouped[entry.host].push(entry.avgCpu);
      });

      let ruSum = 0;
      let ruCount = 0;
      for (const host in grouped) {
        const hostAvg = grouped[host].reduce((a, b) => a + b, 0) / grouped[host].length;
        ruSum += hostAvg;
        ruCount++;
      }
      const realCpuPercentage = ruCount > 0 ? ruSum / ruCount : 0;
      
      // 2. CloudSim-like Resource Utilization using actual CPU time from monitors
      const totalMonitoredCpuTime = cpuUsages.reduce((sum, entry) => sum + (entry.totalCpuTime || 0), 0);
      const totalHostCapacity = workers.length * (makespanMs / 1000); // Total host seconds available
      const monitorBasedRU = totalHostCapacity > 0 ? (totalMonitoredCpuTime / totalHostCapacity) * 100 : 0;
      
      // 3. Theoretical Resource Utilization (Task execution time based - like CloudSim)
      const theoreticalRU = (totalExecutionTime / (makespanMs * workers.length)) * 100;
      
      // Use CloudSim-like approach as primary resource utilization
      const resourceUtilization = monitorBasedRU;
      

      
      // Calculate average waiting time from individual task waiting times (like CloudSim)
      const totalWaitingTime = allTaskResults.reduce((sum, result) => sum + (result.waitingTime || 0), 0);
      const avgWaitingTime = totalWaitingTime / completedTasks;
      
      // Calculate start and finish times like CloudSim (absolute timestamps from simulation start)
      const totalStartTime = allTaskResults.reduce((sum, result) => sum + (result.startTime - simulationStart), 0);
      const avgStartTime = totalStartTime / completedTasks;
      
      const totalFinishTime = allTaskResults.reduce((sum, result) => sum + (result.finishTime - simulationStart), 0);
      const avgFinishTime = totalFinishTime / completedTasks;
      
      // Scheduling Length: Total waiting time + makespan (like CloudSim)
      const schedulingLength = totalWaitingTime + makespanMs;

      console.log(`\n======== ABC ${USE_EOBL ? '+ EOBL' : '(Standard)'} RESULTS ========`);
      console.log(`✅ All tasks completed with ABC ${USE_EOBL ? '+ EOBL' : '(Standard)'}.`);
      console.log(`🕒 Task Execution Duration (excluding mapping): ${simulationDurationSec.toFixed(6)} seconds`);
      console.log(`🕒 Makespan (pure execution time): ${makespanMs.toFixed(6)} ms`);
      console.log(`📈 Throughput: ${throughput.toFixed(6)} tasks/second`);
      console.log(`⏱️ Avg Waiting Time: ${avgWaitingTime.toFixed(6)} ms`);
      console.log(`💡 Real CPU Percentage (Monitor): ${realCpuPercentage.toFixed(6)}%`);
      console.log(`💡 CloudSim-like Resource Utilization (CPU Time): ${resourceUtilization.toFixed(6)}%`);
      console.log(`💡 Theoretical Resource Utilization (Task): ${theoreticalRU.toFixed(6)}%`);
      console.log(`📊 Avg Start Time: ${avgStartTime.toFixed(6)} ms`);
      console.log(`📊 Avg Finish Time: ${avgFinishTime.toFixed(6)} ms`);
      console.log(`📊 Avg Execution Time: ${avgExecutionTime.toFixed(6)} ms`);
      console.log(`⚖️ Imbalance Degree: ${imbalanceDegree.toFixed(6)}`);
      console.log(`📋 Scheduling Length: ${schedulingLength.toFixed(6)} ms`);
      


      // Save summary to CSV with proper formatting and units (matching CloudSim format)
      const csvHeader = [
        'Algorithm',
        'Task Execution Duration (s)',
        'Makespan (s)', 
        'Throughput (tasks/s)',
        'Avg Waiting Time (ms)',
        'Real CPU Percentage (%)',
        'CloudSim-like Resource Utilization (%)',
        'Theoretical Resource Utilization (%)',
        'Avg Start (ms)',
        'Avg Finish (ms)',
        'Avg Execution Time (ms)',
        'Imbalance Degree',
        'Scheduling Length (ms)'
      ].join(',') + '\n';

      const csvRow = [
        USE_EOBL ? 'ABC+EOBL' : 'ABC_Standard',
        simulationDurationSec.toFixed(6),
        (makespanMs / 1000).toFixed(6), // Convert to seconds for CSV like simulation
        throughput.toFixed(6),
        avgWaitingTime.toFixed(6),
        realCpuPercentage.toFixed(6),
        resourceUtilization.toFixed(6),
        theoreticalRU.toFixed(6),
        avgStartTime.toFixed(6),
        avgFinishTime.toFixed(6),
        avgExecutionTime.toFixed(6),
        imbalanceDegree.toFixed(6),
        schedulingLength.toFixed(6)
      ].join(',') + '\n';

      const csvPath = path.join(__dirname, 'ABC_Results.csv');
      let writeHeader = false;
      if (!fs.existsSync(csvPath)) writeHeader = true;
      fs.appendFileSync(csvPath, (writeHeader ? csvHeader : '') + csvRow);
      
      console.log(`📄 Results saved to: ${csvPath}`);

    }

    res.json({
      status: 'batch_completed',
      batchIndex: batchIndex - 1,
              tasksInBatch: batchResults.length,
        completedTasks: completedTasks,
        totalTasks: totalTasks
            });

        console.log('📋 Task distribution completed');
      } catch (err) {
    res.status(500).json({
      error: 'Batch execution failed',
      batchIndex,
      details: err.message
    });
  }
});

app.post('/reset', (req, res) => {
  batchIndex = 0;
  completedTasks = 0;
  simulationStart = null;
  simulationEnd = null;
  abcMapping = [];
  totalCost = 0;
  cpuUsages.length = 0;
  allTaskResults.length = 0; // Reset task results array
  workerMipsArray.length = 0; // Reset MIPS data
  
  // Reset worker data
  workers.forEach(worker => {
    workerExecutionData[worker] = {
      tasks: [],
      totalExecutionTime: 0,
      startTime: null,
      endTime: null
    };
    workerFinishTimes[worker] = 0;
  });

  res.json({ status: 'reset done' });
});

app.listen(8080, () => {
  console.log(`🚀 Broker running on port 8080 (ABC ${USE_EOBL ? '+ EOBL' : 'Standard'} ENABLED)`);
});