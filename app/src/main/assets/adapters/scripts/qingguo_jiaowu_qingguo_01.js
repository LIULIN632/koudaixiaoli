/**
 * 青果教务系统通用适配脚本
 * 采用“倒数 7 列”逻辑，解决星期偏移
 */

function parseWeeks(weekStr) {
    const weeks = [];
    // Detect 单/双 flag
    let flag = 0;
    if (weekStr.includes('(单)') || weekStr.includes('单周')) flag = 1;
    else if (weekStr.includes('(双)') || weekStr.includes('双周')) flag = 2;
    // Clean markers
    const cleaned = weekStr.replace(/\(单\)|\(双\)|单周|双周/g, '').trim();
    cleaned.split(',').forEach(part => {
        if (part.includes('-')) {
            const [start, end] = part.split('-').map(Number);
            if (!isNaN(start) && !isNaN(end)) {
                for (let i = start; i <= end; i++) {
                    if (flag === 1 && i % 2 !== 1) continue;
                    if (flag === 2 && i % 2 !== 0) continue;
                    weeks.push(i);
                }
            }
        } else {
            const w = parseInt(part);
            if (!isNaN(w)) {
                if (flag === 1 && w % 2 !== 1) return;
                if (flag === 2 && w % 2 !== 0) return;
                weeks.push(w);
            }
        }
    });
    return weeks;
}

async function fetchAndParseCourses() {
    const rawItems = [];
    
    function findTable(win) {
        const t = Array.from(win.document.querySelectorAll('table')).find(x => x.innerText.includes("星期一") && x.innerText.includes("["));
        if (t) return t;
        for (let i = 0; i < win.frames.length; i++) {
            try { const st = findTable(win.frames[i]); if (st) return st; } catch (e) {}
        }
        return null;
    }

    const table = findTable(window);
    if (!table) return null;

    // 原始数据清洗抓取
    Array.from(table.rows).forEach(row => {
        const cells = Array.from(row.cells);
        if (cells.length < 7) return;

        cells.forEach((cell, colIndex) => {
            const distanceToLast = cells.length - 1 - colIndex;
            if (distanceToLast > 6) return; 
            const day = 7 - distanceToLast;

            const rawText = cell.innerText.trim();
            if (!rawText.includes('[')) return;

            // 过滤掉空白行，并清洗每一行的首尾空格
            const lines = rawText.split('\n').map(l => l.trim()).filter(l => l);
            
            lines.forEach((line, i) => {
                const match = line.match(/([\d\-,]+)\[(\d+)-(\d+)\]/);
                if (match) {
                    let name = "未知课程";
                    if (i >= 2) name = lines[i-2];
                    else if (i >= 1) name = lines[i-1];

                    let teacher = (i >= 1 && !lines[i-1].includes('[')) ? lines[i-1] : "未知教师";
                    let position = (i < lines.length - 1) ? lines[i+1] : "未知地点";

                    rawItems.push({
                        name: name.replace(/\s/g, ""), // 去除所有空格
                        teacher: teacher.replace(/\s/g, ""),
                        position: position.replace(/\s/g, ""),
                        day: day,
                        startSection: parseInt(match[2]),
                        endSection: parseInt(match[3]),
                        weeks: parseWeeks(match[1])
                    });
                }
            });
        });
    });

    // 矩阵合并
    const groupMap = new Map();
    rawItems.forEach(item => {
        const key = `${item.name}|${item.teacher}|${item.position}|${item.day}`;
        if (!groupMap.has(key)) groupMap.set(key, {});
        
        const weekMap = groupMap.get(key);
        item.weeks.forEach(w => {
            if (!weekMap[w]) weekMap[w] = new Set();
            for (let s = item.startSection; s <= item.endSection; s++) {
                weekMap[w].add(s);
            }
        });
    });

    const finalCourses = [];
    groupMap.forEach((weekMap, key) => {
        const [name, teacher, position, day] = key.split('|');
        
        // 模式聚合：寻找具有相同“节次跨度”的周次
        const patternMap = new Map(); 

        Object.keys(weekMap).forEach(w => {
            const week = parseInt(w);
            const sections = Array.from(weekMap[week]).sort((a, b) => a - b);
            if (sections.length === 0) return;

            // 重新切分连续节次
            let start = sections[0];
            for (let i = 0; i < sections.length; i++) {
                if (i === sections.length - 1 || sections[i+1] !== sections[i] + 1) {
                    const pKey = `${start}-${sections[i]}`;
                    if (!patternMap.has(pKey)) patternMap.set(pKey, []);
                    patternMap.get(pKey).push(week);
                    if (i < sections.length - 1) start = sections[i+1];
                }
            }
        });

        patternMap.forEach((weeks, pKey) => {
            const [sStart, sEnd] = pKey.split('-').map(Number);
            finalCourses.push({
                name, teacher, position,
                day: parseInt(day),
                startSection: sStart,
                endSection: sEnd,
                weeks: weeks.sort((a, b) => a - b)
            });
        });
    });

    return finalCourses;
}

async function runImportFlow() {
    try {
        AndroidBridge.showToast("正在合并课表数据...");
        const courses = await fetchAndParseCourses();
        if (!courses || courses.length === 0) {
            AndroidBridge.showToast("未找到可导入课程");
            return;
        }
        await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(courses));
        AndroidBridge.showToast(`成功：已优化合并为 ${courses.length} 个课块`);
        AndroidBridge.notifyTaskCompletion();
    } catch (error) {
        AndroidBridge.showToast("解析失败: " + error.message);
    }
}

runImportFlow();
