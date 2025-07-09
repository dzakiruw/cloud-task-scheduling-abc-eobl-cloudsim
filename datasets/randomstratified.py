import random
import os
import json

kelas = [
    ("class 1", 862), ("class 2", 31), ("class 3", 14), ("class 4", 6), ("class 5", 10),
    ("class 6", 8), ("class 7", 4), ("class 8", 1), ("class 9", 1), ("class 10", 1),
    ("class 11", 2), ("class 12", 10), ("class 13", 0), ("class 14", 1), ("class 15", 1),
    ("class 16", 2), ("class 17", 1), ("class 18", 1), ("class 19", 1), ("class 20", 3),
    ("class 21", 1), ("class 22", 1), ("class 23", 2), ("class 24", 1), ("class 25", 1),
    ("class 26", 0), ("class 27", 0), ("class 28", 0), ("class 29", 1), ("class 30", 3),
    ("class 31", 1), ("class 32", 2), ("class 33", 2), ("class 34", 3), ("class 35", 2),
    ("class 36", 0), ("class 37", 1), ("class 38", 1), ("class 39", 1), ("class 40", 1),
    ("class 41", 0), ("class 42", 1), ("class 43", 0), ("class 44", 0), ("class 45", 1),
    ("class 46", 0), ("class 47", 2), ("class 48", 0), ("class 49", 0), ("class 50", 0),
    ("class 51", 0), ("class 52", 0), ("class 53", 0), ("class 54", 0), ("class 55", 0),
    ("class 56", 1), ("class 57", 1), ("class 58", 0), ("class 59", 0), ("class 60", 0),
    ("class 61", 1), ("class 62", 0), ("class 63", 0), ("class 64", 1), ("class 65", 0),
    ("class 66", 0), ("class 67", 1), ("class 68", 1), ("class 69", 1), ("class 70", 0),
    ("class 71", 0), ("class 72", 0), ("class 73", 0), ("class 74", 0), ("class 75", 1),
    ("class 76", 0), ("class 77", 0), ("class 78", 0), ("class 79", 1), ("class 80", 0),
    ("class 81", 0), ("class 82", 0), ("class 83", 0), ("class 84", 0), ("class 85", 0),
    ("class 86", 0), ("class 87", 0), ("class 88", 0), ("class 89", 0), ("class 90", 0),
    ("class 91", 0), ("class 92", 0), ("class 93", 0), ("class 94", 1), ("class 95", 1),
    ("class 96", 0), ("class 97", 0), ("class 98", 0), ("class 99", 0), ("class 100", 1),
]

folder_path = 'generateRandomStrat'
dir_path = f'datasets/randomStratified/{folder_path}'

os.makedirs(dir_path, exist_ok=True)

step = 500
min_total = 1000
max_total = 10000

total_kelas_asal = sum([jumlah for _, jumlah in kelas])

for target_total in range(min_total, max_total + 1, step):
    print(f"\nProcessing dataset dengan total {target_total} data...")
    # 1. Hitung proporsi & jumlah float tiap kelas
    jumlah_float = []
    sisa_pecahan = []
    for nama, jumlah in kelas:
        prop = jumlah / total_kelas_asal if total_kelas_asal != 0 else 0
        jml = prop * target_total
        jumlah_float.append(jml)
        sisa_pecahan.append(jml - int(jml))

    # 2. Ambil integer (floor) dulu
    jumlah_int = [int(jml) for jml in jumlah_float]
    # 3. Hitung kekurangan (total_int harus pas)
    kekurangan = target_total - sum(jumlah_int)

    # 4. Tambahkan sisa ke kelas dengan sisa pecahan terbesar
    urut_sisa = sorted(
        list(enumerate(sisa_pecahan)), key=lambda x: x[1], reverse=True
    )
    for i in range(kekurangan):
        idx = urut_sisa[i][0]
        jumlah_int[idx] += 1

    # 5. Generate data random per kelas
    start = 0
    end = 88000
    folder_dataset = f'{dir_path}/dataset{target_total}'
    os.makedirs(folder_dataset, exist_ok=True)
    for idx, (nama, _) in enumerate(kelas):
        total_data_kelas = jumlah_int[idx]
        data = [random.randint(start, end) for _ in range(total_data_kelas)]
        with open(f'{folder_dataset}/{nama.replace(" ", "_")}.json', "w") as f:
            json.dump({"data": data}, f)
        start = end
        end += 88000

    # 6. Gabungkan seluruh data jadi satu file txt
    data_all = []
    for i in range(1, 101):
        filejson = f"{folder_dataset}/class_{i}.json"
        if os.path.exists(filejson):
            with open(filejson) as jf:
                data_row = json.load(jf)["data"]
                data_all.extend(data_row)

    txt_path = f'{folder_dataset}/RandStratified{target_total}.txt'
    with open(txt_path, "w") as f:
        for item in data_all:
            f.write(f"{item}\n")

    # 7. Hapus newline terakhir agar rapi (opsional)
    with open(txt_path, "rb+") as file:
        file.seek(-2, os.SEEK_END)
        file.truncate()

    # Cek jumlah total data
    print(f"  Sukses generate {len(data_all)} data (target: {target_total})")

