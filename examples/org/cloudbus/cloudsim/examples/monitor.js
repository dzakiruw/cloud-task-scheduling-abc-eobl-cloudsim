const { exec } = require('child_process');
const axios = require('axios');

// Ganti dengan URL broker Anda
const BROKER_URL = 'http://192.168.56.10:8080/cpu-usage-report';

// Hardcoded HOST_ID untuk testing (no auto-detection needed)
const HOST_ID = 'host-3';
console.log(`🔍 Using hardcoded HOST_ID: ${HOST_ID}`);

// No need for Express server - just CPU monitoring
console.log(`📡 CPU Monitor starting for ${HOST_ID}...`);

// Ambil CPU usage dan CPU time dari semua container Docker yang aktif
function getCPUMetrics(callback) {
  const cmd = 'sudo docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.PIDs}}"';

  exec(cmd, (err, stdout) => {
    if (err) return callback(err, null);

    const lines = stdout.trim().split('\n');
    const containers = lines.map(line => {
      const parts = line.split(',');
      return {
        name: parts[0],
        cpuPerc: parseFloat(parts[1].replace('%', '')),
        pids: parseInt(parts[2]) || 0
      };
    }).filter(container => !isNaN(container.cpuPerc));

    // Calculate average CPU percentage (like before)
    const avgCpu = containers.length > 0
      ? containers.reduce((sum, c) => sum + c.cpuPerc, 0) / containers.length
      : 0;

    // Calculate total CPU time used (simulating CloudSim approach)
    // CPU Time = (CPU% / 100) * sampling_interval_seconds * number_of_cores
    const samplingInterval = 1.0; // 1 second sampling
    const estimatedCores = 2; // Assume 2 cores per worker container
    
    const totalCpuTimeUsed = containers.reduce((sum, c) => {
      const cpuTimeForContainer = (c.cpuPerc / 100) * samplingInterval * estimatedCores;
      return sum + cpuTimeForContainer;
    }, 0);

    callback(null, {
      avgCpu: avgCpu,
      totalCpuTime: totalCpuTimeUsed,
      activeContainers: containers.length
    });
  });
}

// Kirim data CPU usage dan CPU time ke broker
function sendCpuUsage() {
  getCPUMetrics((err, metrics) => {
    if (err) {
      console.error('❌ Gagal mendapatkan CPU metrics:', err.message);
      return;
    }

    axios.post(BROKER_URL, {
      host: HOST_ID,
      avgCpu: metrics.avgCpu,
      totalCpuTime: metrics.totalCpuTime,
      activeContainers: metrics.activeContainers,
      timestamp: Date.now()
    }).then(() => {
      console.log(`📤 CPU metrics terkirim dari ${HOST_ID}: ${metrics.avgCpu.toFixed(2)}% (CPU Time: ${metrics.totalCpuTime.toFixed(4)}s)`);
    }).catch(err => {
      console.error('❌ Gagal mengirim ke broker:', err.message);
    });
  });
}

// Jalankan setiap 1 detik
setInterval(sendCpuUsage, 1000);

console.log(`📡 Monitor CPU aktif untuk ${HOST_ID}...`);
