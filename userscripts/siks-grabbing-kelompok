// Paste di DevTools Console halaman p2k2 (grid absensi sudah termuat)
(() => {
  const root = [...document.querySelectorAll(".MuiDataGrid-root")][1];
  const key = Object.keys(root).find((k) => k.startsWith("__react"));
  let fiber = root[key], rows = null;
  for (let d = 0; fiber && d < 15; d++, fiber = fiber.return) {
    if (Array.isArray(fiber.memoizedProps?.rows)) { rows = fiber.memoizedProps.rows; break; }
  }
  if (!rows) return console.warn("rows tidak ditemukan");

  const data = rows.map((r) => ({
    nama: r.nama,
    no_kk: r.no_kk,
    nik: r.nik,              // full, tidak dimask
    nama_kelompok: r.nama_kelompok,
    sta_absensi: r.sta_absensi,
  }));
  console.table(data.slice(0, 10));

  const csv = [Object.keys(data[0]).join(","),
    ...data.map((r) => Object.values(r).map((v) => `"${v ?? ""}"`).join(","))].join("\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  a.download = "p2k2-nik-unmasked.csv";
  a.click();
  console.log(`✅ ${data.length} baris -> p2k2-nik-unmasked.csv`);
  return data;
})();
