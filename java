"use strict";

const byId = id => document.getElementById(id);
const fileInput = byId("file-input");
const dropzone = byId("dropzone");
const fileList = byId("file-list");
const runButton = byId("run-demo");
const demoButton = byId("reset-demo");
const excelButton = byId("download-excel");
const chartButton = byId("download-chart");
const canvas = byId("chart");
const tooltip = byId("chart-tooltip");
const stepElements = Array.from(byId("steps").children);
const toggles = Array.from(document.querySelectorAll(".series-toggle"));
const tabButtons = [byId("chart-tab"), byId("table-tab")];
const palette = { raw: "#889aff", corrected: "#78e6ce" };
const shown = { raw: true, corrected: true };
const sampleFiles = [];
let running = false;
let pickedPoint = null;

function demoData() {
  const peak = (wavelength, center, width) => Math.exp(-(((wavelength - center) / width) ** 2) / 2);
  return Array.from({ length: 136 }, (_, index) => {
    const wavelength = 380 + index * 4;
    const raw = 0.04 + 0.57 * peak(wavelength, 458, 27) + 0.67 * peak(wavelength, 648, 83) +
      0.12 * peak(wavelength, 775, 38) + 0.007 * Math.sin(index * 1.1);
    const corrected = 0.05 + 0.66 * peak(wavelength, 463, 31) + 0.92 * peak(wavelength, 664, 82) +
      0.13 * peak(wavelength, 810, 46) + 0.006 * Math.sin(index * 1.1 + 0.6);
    return { wavelength, raw: Number(raw.toFixed(4)), corrected: Number(corrected.toFixed(4)) };
  });
}

let results = demoData();

function renderFileList() {
  fileList.replaceChildren();
  byId("file-count").textContent = sampleFiles.length + " 个文件";
  for (const [index, file] of sampleFiles.entries()) {
    const row = document.createElement("li");
    const label = document.createElement("span");
    label.textContent = file.name;
    label.title = file.name;
    const remove = document.createElement("button");
    remove.className = "remove-file";
    remove.type = "button";
    remove.disabled = running;
    remove.setAttribute("aria-label", "移除 " + file.name);
    remove.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>';
    remove.addEventListener("click", () => {
      sampleFiles.splice(index, 1);
      renderFileList();
      (fileList.querySelectorAll("button")[index] || fileList.lastElementChild?.querySelector("button") || fileInput).focus();
    });
    row.append(label, remove);
    fileList.append(row);
  }
}

function addFiles(files) {
  if (running) return;
  const skipped = [];
  for (const file of files) {
    if (!/\.xlsx?$/i.test(file.name)) {
      skipped.push(file.name);
      continue;
    }
    if (!sampleFiles.some(existing => existing.name === file.name && existing.size === file.size &&
      existing.lastModified === file.lastModified)) {
      sampleFiles.push(file);
    }
  }
  byId("file-notice").textContent = skipped.length ? "请选择 .xlsx 或 .xls 文件。已跳过 " + skipped.length + " 个其他文件。" : "";
  renderFileList();
}

fileInput.addEventListener("change", () => {
  addFiles(fileInput.files);
  fileInput.value = "";
});

for (const eventName of ["dragenter", "dragover"]) {
  dropzone.addEventListener(eventName, event => {
    event.preventDefault();
    if (!running) dropzone.classList.add("dragging");
  });
}
dropzone.addEventListener("dragleave", event => {
  if (!dropzone.contains(event.relatedTarget)) dropzone.classList.remove("dragging");
});
dropzone.addEventListener("drop", event => {
  event.preventDefault();
  dropzone.classList.remove("dragging");
  addFiles(event.dataTransfer.files);
});

function updateSteps(activeCount) {
  stepElements.forEach((step, index) => {
    step.dataset.status = index < activeCount ? "done" : index === activeCount ? "running" : "waiting";
    if (index === activeCount) step.setAttribute("aria-current", "step");
    else step.removeAttribute("aria-current");
  });
  byId("run-status").textContent = activeCount < stepElements.length
    ? (activeCount + 1) + " / " + stepElements.length + " 进行中"
    : "示例已完成";
}

// Integration point: replace the demo with a processor returning the same row shape.
async function analyzeFiles(files, progress) {
  for (let step = 0; step < stepElements.length; step += 1) {
    progress(step);
    await new Promise(resolve => setTimeout(resolve, 360));
  }
  return demoData();
}

async function runPreview(files) {
  if (running) return;
  running = true;
  for (const control of [runButton, demoButton, fileInput, chartButton, excelButton]) control.disabled = true;
  dropzone.classList.add("busy");
  renderFileList();
  byId("results-body").setAttribute("aria-busy", "true");
  byId("export-status").textContent = "";
  try {
    results = await analyzeFiles(files, updateSteps);
    clearPoint();
    renderResults();
    updateSteps(stepElements.length);
  } catch {
    byId("run-status").textContent = "运行失败，可重新尝试";
    for (const step of stepElements) {
      if (step.dataset.status === "running") step.dataset.status = "waiting";
      step.removeAttribute("aria-current");
    }
  } finally {
    running = false;
    for (const control of [runButton, demoButton, fileInput, chartButton, excelButton]) control.disabled = false;
    dropzone.classList.remove("busy");
    renderFileList();
    byId("results-body").setAttribute("aria-busy", "false");
  }
}

runButton.addEventListener("click", () => runPreview([...sampleFiles]));
demoButton.addEventListener("click", () => runPreview([]));

function selectTab(selected) {
  tabButtons.forEach((button, index) => {
    const active = index === selected;
    button.setAttribute("aria-selected", String(active));
    button.tabIndex = active ? 0 : -1;
    byId(button.getAttribute("aria-controls")).hidden = !active;
  });
  clearPoint();
  renderChart();
}

tabButtons.forEach((button, index) => {
  button.addEventListener("click", () => selectTab(index));
  button.addEventListener("keydown", event => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const target = event.key === "Home" ? 0 : event.key === "End" ? 1 : 1 - index;
    selectTab(target);
    tabButtons[target].focus();
  });
});

function chartGeometry(width, height, exportImage) {
  const left = exportImage ? 76 : 47;
  const right = width - (exportImage ? 36 : 19);
  const top = exportImage ? 110 : 35;
  const bottom = height - (exportImage ? 69 : 43);
  const minWave = results[0].wavelength;
  const maxWave = results.at(-1).wavelength;
  const maxSignal = 1.1;
  return {
    left, right, top, bottom, minWave, maxWave, maxSignal,
    x: wave => left + (wave - minWave) / (maxWave - minWave) * (right - left),
    y: signal => bottom - signal / maxSignal * (bottom - top),
  };
}

function draw(target, width, height, scale, exportImage = false) {
  const context = target.getContext("2d");
  context.setTransform(scale, 0, 0, scale, 0, 0);
  context.fillStyle = "#0b1426";
  context.fillRect(0, 0, width, height);
  const { left, right, top, bottom, minWave, maxWave, maxSignal, x, y } = chartGeometry(width, height, exportImage);
  context.fillStyle = "#a6b5ce";
  context.font = (exportImage ? 15 : 12) + "px system-ui, sans-serif";
  context.fillText("相对强度", left, top - 15);
  context.strokeStyle = "#24334f";
  context.lineWidth = 1;
  for (let tick = 0; tick <= 5; tick += 1) {
    const signal = tick / 5;
    const position = y(signal);
    context.beginPath();
    context.moveTo(left, position);
    context.lineTo(right, position);
    context.stroke();
    context.textAlign = "right";
    context.fillText(signal.toFixed(1), left - 9, position + 4);
  }
  const divisions = width < 420 ? 3 : 6;
  for (let index = 0; index <= divisions; index += 1) {
    const wave = minWave + (maxWave - minWave) * index / divisions;
    const position = x(wave);
    context.beginPath();
    context.moveTo(position, top);
    context.lineTo(position, bottom);
    context.stroke();
    context.textAlign = "center";
    context.fillText(Math.round(wave), position, bottom + 18);
  }
  context.fillText("波长 (nm)", (left + right) / 2, bottom + (exportImage ? 52 : 36));

  for (const key of ["corrected", "raw"]) {
    if (!shown[key]) continue;
    context.beginPath();
    results.forEach((point, index) => {
      const coords = [x(point.wavelength), y(Math.min(point[key], maxSignal))];
      if (index === 0) context.moveTo(...coords);
      else context.lineTo(...coords);
    });
    context.strokeStyle = palette[key];
    context.lineWidth = key === "raw" ? 1.65 : 2.5;
    context.setLineDash(key === "raw" ? [6, 4] : []);
    context.lineJoin = "round";
    context.stroke();
    if (key === "corrected") {
      context.lineTo(right, bottom);
      context.lineTo(left, bottom);
      context.closePath();
      const fill = context.createLinearGradient(0, top, 0, bottom);
      fill.addColorStop(0, "#78e6ce2c");
      fill.addColorStop(1, "#78e6ce03");
      context.fillStyle = fill;
      context.fill();
    }
    context.setLineDash([]);
  }
  if (exportImage) {
    context.textAlign = "left";
    context.fillStyle = "#edf1fa";
    context.font = "600 26px system-ui, sans-serif";
    context.fillText("matrix challenger · 示例光谱", left, 42);
    context.font = "15px system-ui, sans-serif";
    let labelX = left;
    for (const key of ["raw", "corrected"]) {
      if (!shown[key]) continue;
      context.fillStyle = palette[key];
      context.fillText(key === "raw" ? "原始信号" : "校准信号", labelX, 70);
      labelX += 99;
    }
  } else if (pickedPoint !== null) {
    const point = results[pickedPoint];
    const position = x(point.wavelength);
    context.strokeStyle = "#9aafd4";
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(position, top);
    context.lineTo(position, bottom);
    context.stroke();
    for (const key of ["raw", "corrected"]) {
      if (!shown[key]) continue;
      context.fillStyle = palette[key];
      context.beginPath();
      context.arc(position, y(point[key]), 4, 0, 2 * Math.PI);
      context.fill();
    }
  }
}

function renderChart() {
  const { width, height } = canvas.getBoundingClientRect();
  if (width === 0 || height === 0) return;
  const scale = Math.min(window.devicePixelRatio || 1, 3);
  canvas.width = Math.round(width * scale);
  canvas.height = Math.round(height * scale);
  draw(canvas, width, height, scale);
}

function clearPoint() {
  pickedPoint = null;
  tooltip.hidden = true;
  byId("point-readout").firstElementChild.textContent = "校准信号峰值波长";
  byId("peak-value").textContent = results.reduce((peak, point) => point.corrected > peak.corrected ? point : peak).wavelength + " nm";
}

function inspectPoint(index, withTooltip) {
  pickedPoint = index;
  const point = results[index];
  const { width, height } = canvas.getBoundingClientRect();
  const labels = [];
  if (shown.raw) labels.push("原始 " + point.raw.toFixed(4));
  if (shown.corrected) labels.push("校准 " + point.corrected.toFixed(4));
  byId("point-readout").firstElementChild.textContent = point.wavelength + " nm";
  byId("peak-value").textContent = labels.join(" · ");
  tooltip.hidden = !withTooltip;
  if (withTooltip) {
    tooltip.textContent = [point.wavelength + " nm", ...labels].join("\n");
    const position = chartGeometry(width, height, false).x(point.wavelength);
    tooltip.style.left = Math.max(6, Math.min(position + 12, width - tooltip.offsetWidth - 6)) + "px";
    tooltip.style.top = "9px";
  }
  renderChart();
}

canvas.addEventListener("pointermove", event => {
  const { width, height, left: offset } = canvas.getBoundingClientRect();
  const { left, right } = chartGeometry(width, height, false);
  const fraction = Math.max(0, Math.min(1, (event.clientX - offset - left) / (right - left)));
  inspectPoint(Math.round(fraction * (results.length - 1)), true);
});
for (const event of ["pointerleave", "blur"]) {
  canvas.addEventListener(event, () => { clearPoint(); renderChart(); });
}
canvas.addEventListener("keydown", event => {
  const direction = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
  if (!direction) return;
  event.preventDefault();
  inspectPoint(Math.max(0, Math.min(results.length - 1, (pickedPoint ?? Math.floor(results.length / 2)) + direction)), false);
});

for (const button of toggles) {
  button.addEventListener("click", () => {
    const key = button.dataset.series;
    if (shown[key] && Object.values(shown).filter(Boolean).length === 1) return;
    shown[key] = !shown[key];
    button.setAttribute("aria-pressed", String(shown[key]));
    clearPoint();
    renderChart();
  });
}

function renderResults() {
  const body = byId("result-rows");
  const rows = results.map(({ wavelength, raw, corrected }) => {
    const row = document.createElement("tr");
    for (const value of [wavelength, raw.toFixed(4), corrected.toFixed(4)]) {
      row.insertCell().textContent = value;
    }
    return row;
  });
  body.replaceChildren(...rows);
  byId("row-count").textContent = rows.length + " 个波长点";
  clearPoint();
  renderChart();
}

excelButton.addEventListener("click", () => {
  byId("export-status").textContent = "";
  WorkbookExport.download(WorkbookExport.make(results), "matrix-challenger-demo.xlsx");
});
chartButton.addEventListener("click", () => {
  byId("export-status").textContent = "";
  const image = document.createElement("canvas");
  image.width = 1600;
  image.height = 900;
  draw(image, 800, 450, 2, true);
  image.toBlob(blob => {
    if (blob) WorkbookExport.download(blob, "matrix-challenger-spectrum.png");
    else byId("export-status").textContent = "图表导出失败，请再试一次。";
  }, "image/png");
});

new ResizeObserver(renderChart).observe(canvas);
renderResults();
