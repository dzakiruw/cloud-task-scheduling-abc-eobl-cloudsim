const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const crypto = require('crypto');
const app = express();

app.use(express.json());

// Variabel untuk menyimpan MIPS nyata
let realMips = 0;

// Fungsi untuk menghitung MIPS nyata dengan benchmark
function calculateRealMips() {
    const iterations = 100000000; // Jumlah iterasi untuk benchmark
    const startTime = Date.now();
    let sum = 0;

    // Lakukan operasi sederhana (aritmatika) untuk mengukur performa
    for (let i = 0; i < iterations; i++) {
        sum += Math.sin(i) * Math.cos(i); // Operasi floating-point
    }

    const endTime = Date.now();
    const durationSeconds = (endTime - startTime) / 1000; // Durasi dalam detik

    // Asumsi: 1 iterasi = 10 instruksi (sin dan cos masing-masing ~5 instruksi)
    const instructions = iterations * 10; // Total instruksi
    const mips = (instructions / durationSeconds) / 1000000; // Konversi ke MIPS

    console.log(`Calculated Real MIPS: ${mips}`);
    return mips;
}

// Endpoint untuk broker mendapatkan MIPS data
app.get('/api/mips', (req, res) => {
  res.json({
    mips: realMips,
    timestamp: Date.now(),
    status: 'active'
  });
});

app.post('/api/execute', (req, res) => {
  const task = req.body.task || 'ringan';
  const db = new sqlite3.Database('./cloud_tasks.db');
  
  if (task === 'ringan') {
    const startTime = Date.now()
    db.all("SELECT name FROM products LIMIT 8000", (err, rows) => {
      if (err) return res.status(500).json({ error: err.message });

      let hashTotal = 0;
      for (let i = 0; i < 10000; i++) {
        const val = rows[0]?.name || 'default';
        hashTotal += crypto.createHash('md5').update(`light_${i}_${val}`).digest('hex').length;
      }

      const finishedTime = Date.now()
      const executionTime = finishedTime - startTime

      res.json({
        status: 'completed',
        task,
        result: {
          summary: "Light processing with CPU load",
          product_count: rows.length,
          final_hash: hashTotal,
          start_time: startTime,
          finish_time: finishedTime,
          execution_time: executionTime,
          worker_mips: realMips // Include MIPS info in response
        }
      });

      db.close();
    });
  }

  else if (task === 'berat') {
    const startTime = Date.now()
    db.all("SELECT price FROM products ORDER BY price DESC LIMIT 10000", (err, rows) => {
      if (err) return res.status(500).json({ error: err.message });

      const prices = rows.map(r => r.price);
      let total = 0;

      for (let i = 0; i < 30000; i++) {
        total += Math.floor(prices.reduce((a, b) => a + b, 0) * i % 99997);
      }
      const finishedTime = Date.now()
      const executionTime = finishedTime - startTime

      const avg = prices.reduce((a, b) => a + b, 0) / prices.length;

      res.json({
        status: 'completed',
        task,
        result: {
          summary: "Heavy processing with CPU load",
          average_price: avg,
          total,
          price_count: prices.length,
          start_time: startTime,
          finish_time: finishedTime,
          execution_time: executionTime,
          worker_mips: realMips // Include MIPS info in response
        }
      });

      db.close();
    });
  }

  else if (task === 'sedang') {
    const startTime = Date.now()
    db.all("SELECT id FROM products LIMIT 10000", (err1, prodRows) => {
      if (err1) return res.status(500).json({ error: err1.message });
      const product_ids = prodRows.map(r => r.id);

      db.all("SELECT id FROM users LIMIT 10000", (err2, userRows) => {
        if (err2) return res.status(500).json({ error: err2.message });
        const user_ids = userRows.map(r => r.id);

        const combined_hashes = [];
        let count = 0;
        const max = 100000;

        outer:
        for (let p of product_ids) {
          for (let u of user_ids) {
            if (count >= max) break outer;
            const str = `${p}_heavy_${u}_processing`;
            const h = crypto.createHash('sha256').update(str).digest('hex');
            combined_hashes.push(h);
            count++;
          }
        }
       
        const finishedTime = Date.now()
        const executionTime = finishedTime - startTime

        res.json({
          status: 'completed',
          task,
          result: {
            summary: "Medium CPU-bound processing",
            processed_combinations: count,
            sample_hash: combined_hashes.slice(0, 5),
            start_time: startTime,
            finish_time: finishedTime,
            execution_time: executionTime,
            worker_mips: realMips // Include MIPS info in response
          }
        });

        db.close();
      });
    });
  }

  else {
    db.close();
    res.status(400).json({ error: 'Unknown task type' });
  }
});

// No need to send MIPS to host - broker will query directly via /api/mips endpoint

// Hitung MIPS saat startup
console.log('🔧 Calculating real MIPS for this worker...');
realMips = calculateRealMips();
console.log(`✅ Worker MIPS calculation completed: ${realMips.toFixed(6)} MIPS`);

app.listen(3000, () => {
  console.log('Worker running on port 3000');
  console.log(`📊 Worker MIPS: ${realMips.toFixed(6)}`);
  console.log(`🔗 MIPS API available at: /api/mips`);
});


// const express = require('express');
// const app = express();

// app.use(express.json());

// app.post('/api/execute', (req, res) => {
//     const task = req.body.task || 'default';
//     const loads = {
//         a: 2000,
//         b: 3000,
//         c: 4000,
//         d: 5000,
//         e: 6000
//     };
//     const load = loads[task] || 2000;

//     console.log(`Executing task "${task}" with load ${load}`);
//     setTimeout(() => {
//         res.json({ status: 'completed', task, load });
//     }, load);
// });

// app.listen(3000, () => console.log('Worker running on port 3000'));
