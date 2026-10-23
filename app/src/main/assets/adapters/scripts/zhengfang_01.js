// 基于 HTML 页面抓取的拾光课表正方适配脚本
// 更新: iframe支持 + DOM加载延迟 + 详细日志

/**
 * 搜索课表文档（主文档 + iframe）
 */
function findTimetableDoc() {
    console.log('JS: 搜索课表元素 (主文档 + iframe)...');

    const mainGrid = document.querySelector('#kbgrid_table_0');
    const mainList = document.querySelector('#kblist_table');
    if (mainGrid || mainList) {
        console.log('JS: 在主文档中找到课表 (' + (mainGrid ? 'grid' : 'list') + ' 视图)');
        return document;
    }

    const iframes = document.querySelectorAll('iframe');
    console.log('JS: 主文档未找到课表，检查 ' + iframes.length + ' 个iframe...');

    for (let i = 0; i < iframes.length; i++) {
        try {
            var iframeDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;
            if (iframeDoc) {
                var gridTable = iframeDoc.querySelector('#kbgrid_table_0');
                var listTable = iframeDoc.querySelector('#kblist_table');
                if (gridTable || listTable) {
                    console.log('JS: 在iframe[' + i + ']中找到课表 (' + (gridTable ? 'grid' : 'list') + ' 视图)');
                    return iframeDoc;
                }
            }
        } catch(e) {
            console.log('JS: 无法访问iframe[' + i + ']: ' + e.message);
        }
    }

    console.log('JS: 未在任何位置找到课表元素，回退到主文档');
    return document;
}

/**
 * 解析表格
 */
function parserTbale(doc) {
    var regexName = /[●★○]/g;
    var courseInfoList = [];
    var $ = window.jQuery;
    if (!$) return courseInfoList;

    var tdElements = $('#kbgrid_table_0 td', doc || document);
    console.log('JS parserTbale: 找到 ' + tdElements.length + ' 个td元素');

    var tdWithClass = 0, courseDivs = 0;
    tdElements.each(function(i, td) {
        if ($(td).hasClass('td_wrap') && $(td).text().trim() !== '') {
            tdWithClass++;
            var day = parseInt($(td).attr('id').split('-')[0]);

            $(td).find('.timetable_con.text-left').each(function(j, course) {
                courseDivs++;
                var name = $(course).find('.title font').text().replace(regexName, '').trim();
                console.log('JS parserTbale: td[' + i + '] course[' + j + '] name="' + name + '" day=' + day);

                var infoStr = $(course).find('p').eq(0).find('font').eq(1).text().trim();
                var position = $(course).find('p').eq(1).find('font').text().trim();
                var teacher = $(course).find('p').eq(2).find('font').text().trim();

                console.log('JS parserTbale:   infoStr="' + infoStr + '" position="' + position + '" teacher="' + teacher + '"');

                if (infoStr && infoStr.match(/\((\d+-\d+节)\)/) && infoStr.split('节)')[1]) {
                    var parts = parserInfo(infoStr);
                    var sections = parts[0];
                    var weeks = parts[1];

                    if (name && sections.length) {
                        var startSection = sections[0];
                        var endSection = sections[sections.length - 1];
                        var finalPosition = (position || '').split(/\s+/).pop() || '';

                        var data = { name: name, day: day, weeks: weeks.length ? weeks : [], teacher: teacher || '', position: finalPosition, startSection: startSection, endSection: endSection };
                        courseInfoList.push(data);
                        console.log('JS parserTbale:   已添加: 第' + startSection + '-' + endSection + '节 周次=' + weeks.join(','));
                    } else {
                        console.log('JS parserTbale:   跳过: name="' + name + '" sections=' + JSON.stringify(sections));
                    }
                } else {
                    console.log('JS parserTbale:   infoStr格式不匹配，跳过');
                }
            });
        }
    });

    console.log('JS parserTbale: td_wrap=' + tdWithClass + ' courseDivs=' + courseDivs + ' 解析到=' + courseInfoList.length + ' 门课程');
    return courseInfoList;
}

/**
 * 解析列表
 */
function parserList(doc) {
    var regexName = /[●★○]/g;
    var regexWeekNum = /周数：|周/g;
    var regexPosition = /上课地点：/g;
    var regexTeacher = /教师 ：/g;

    var $ = window.jQuery;
    if (!$) return [];

    var courseInfoList = [];
    var tbodies = $('#kblist_table tbody', doc || document);
    console.log('JS parserList: 找到 ' + tbodies.length + ' 个tbody');

    tbodies.each(function(day, tbody) {
        if (day > 0 && day < 8) {
            var sections;
            var trs = $(tbody).find('tr:not(:first-child)');
            console.log('JS parserList: 星期' + day + ' 有 ' + trs.length + ' 行');

            trs.each(function(trIndex, tr) {
                var name, font;

                if ($(tr).find('td').length > 1) {
                    sections = parserSections($(tr).find('td:first-child').text());
                    name = $(tr).find('td:nth-child(2)').find('.title').text().replace(regexName, '').trim();
                    font = $(tr).find('td:nth-child(2)').find('p font');
                } else {
                    name = $(tr).find('td').find('.title').text().replace(regexName, '').trim();
                    font = $(tr).find('td').find('p font');
                }

                var weekStr = $(font[0]).text().replace(regexWeekNum, '').trim();
                var weeks = parserWeeks(weekStr);
                var positionRaw = $(font[1]).text().replace(regexPosition, '').trim();
                var finalPosition = positionRaw.split(/\s+/).pop();
                var teacher = $(font[2]).text().replace(regexTeacher, '').trim();

                console.log('JS parserList: 周' + day + ' row[' + trIndex + '] name="' + name + '" teacher="' + teacher + '" pos="' + finalPosition + '" weeks=' + JSON.stringify(weeks) + ' sections=' + JSON.stringify(sections));

                if (name && sections && weeks.length) {
                    var startSection = sections[0];
                    var endSection = sections[sections.length - 1];

                    var data = {
                        name: name,
                        day: day,
                        weeks: weeks,
                        teacher: teacher || '',
                        position: finalPosition || '',
                        startSection: startSection,
                        endSection: endSection
                    };
                    courseInfoList.push(data);
                    console.log('JS parserList:   已添加: 第' + startSection + '-' + endSection + '节');
                } else {
                    console.log('JS parserList:   跳过: name=' + !!name + ' sections=' + !!sections + ' weeks=' + (weeks.length > 0));
                }
            });
        }
    });

    console.log('JS parserList: 解析到 ' + courseInfoList.length + ' 门课程');
    return courseInfoList;
}

/**
 * 解析课程信息
 */
function parserInfo(str) {
    var sections = parserSections(str.match(/\((\d+-\d+节)\)/)[1].replace(/节/g, ''));
    var weekStrWithMarker = str.split('节)')[1];
    var weeks = parserWeeks(weekStrWithMarker.replace(/周/g, '').trim());
    return [sections, weeks];
}

/**
 * 解析节次
 */
function parserSections(str) {
    var parts = str.split('-').map(Number);
    if (isNaN(parts[0]) || isNaN(parts[1]) || parts[0] > parts[1]) return [];
    return Array.from({ length: parts[1] - parts[0] + 1 }, function(_, i) { return parts[0] + i; });
}

/**
 * 解析周次
 */
function parserWeeks(str) {
    var segments = str.split(',');
    var weeks = [];
    var segmentRegex = /(\d+)(?:-(\d+))?\s*(\([单双]\))?/g;

    for (var si = 0; si < segments.length; si++) {
        var segment = segments[si];
        var cleanSegment = segment.replace(/周/g, '').trim();
        segmentRegex.lastIndex = 0;

        var match;
        while ((match = segmentRegex.exec(cleanSegment)) !== null) {
            var start = parseInt(match[1]);
            var end = match[2] ? parseInt(match[2]) : start;
            var flagStr = match[3] || '';

            var flag = 0;
            if (flagStr.indexOf('单') >= 0) {
                flag = 1;
            } else if (flagStr.indexOf('双') >= 0) {
                flag = 2;
            }

            for (var i = start; i <= end; i++) {
                if (flag === 1 && i % 2 !== 1) continue;
                if (flag === 2 && i % 2 !== 0) continue;
                if (weeks.indexOf(i) < 0) {
                    weeks.push(i);
                }
            }
        }
    }

    return weeks.sort(function(a, b) { return a - b; });
}

async function scrapeAndParseCourses() {
    AndroidBridge.showToast("正在检查页面并抓取课程数据...");
    var ts = "1.登陆教务系统\n2.导航到学生课表查询页面\n3.等待课表信息加载，选择对应学年、学期，确认无误后点击【查询】\n4.确保页面上显示了课程表\n5.点击下方【一键导入】";

    try {
        // 延迟确保动态DOM加载完成
        console.log('JS: 等待 800ms 确保动态DOM加载完成...');
        await new Promise(function(resolve) { setTimeout(resolve, 800); });
        console.log('JS: 延迟结束，开始搜索课表...');

        // 搜索主文档 + iframe
        var searchDoc = findTimetableDoc();

        // 验证页面内容
        var response = await fetch(window.location.href);
        var text = await response.text();
        if (!text.includes("课表查询")) {
            console.log("JS: 页面内容检查失败 - 不含'课表查询'");
            await window.AndroidBridgePromise.showAlert("导入失败", "当前页面似乎不是学生课表查询页面。请检查：\n" + ts, "确定");
            return null;
        }
        console.log("JS: 页面内容检查通过 (含'课表查询')");

        var typeElement = document.querySelector('#shcPDF') || searchDoc.querySelector('#shcPDF');
        if (!typeElement) {
            console.log("JS: 未能找到视图类型元素 (#shcPDF)");
            await window.AndroidBridgePromise.showAlert("导入失败", "未能识别课表视图类型，请确认您已点击查询且课表已加载完毕。", "确定");
            return null;
        }
        var type = typeElement.dataset['type'];
        console.log('JS: 课表视图类型: ' + type);

        var tableElement = searchDoc.querySelector(type === 'list' ? '#kblist_table' : '#kbgrid_table_0');
        if (!tableElement) {
            console.log('JS: 未能找到课表主体 HTML (type=' + type + ')');
            await window.AndroidBridgePromise.showAlert("导入失败", "未能找到课表主体 (" + type + " 视图)，请确认您已点击查询且课表已加载完毕。", "确定");
            return null;
        }
        console.log('JS: 找到课表主体元素, rows=' + tableElement.querySelectorAll('tr').length);

        var result = [];
        if (type === 'list') {
            result = parserList(searchDoc);
        } else {
            result = parserTbale(searchDoc);
        }

        if (result.length === 0) {
            AndroidBridge.showToast("未找到任何课程数据，请检查所选学年学期是否正确或本学期无课。");
            console.log('JS: 解析结果为空');
            return null;
        }
        console.log('JS: 课程数据解析成功，共找到 ' + result.length + ' 门课程');
        return { courses: result };
    } catch (error) {
        AndroidBridge.showToast("抓取或解析失败: " + error.message);
        console.error('JS: Scrape/Parse Error:', error);
        await window.AndroidBridgePromise.showAlert("抓取或解析失败", "发生错误：" + error.message + "。请重试或联系开发者。", "确定");
        return null;
    }
}

async function saveCourses(parsedCourses) {
    AndroidBridge.showToast("正在保存 " + parsedCourses.length + " 门课程...");
    console.log('JS: 尝试保存 ' + parsedCourses.length + ' 门课程...');
    try {
        await window.AndroidBridgePromise.saveImportedCourses(JSON.stringify(parsedCourses, null, 2));
        console.log("JS: 课程保存成功！");
        return true;
    } catch (error) {
        AndroidBridge.showToast("课程保存失败: " + error.message);
        console.error('JS: Save Courses Error:', error);
        return false;
    }
}


// ── Regex-based HTML parser (ported from Dawn-Course zhengfang.js) ──
// Used as fallback when jQuery is not available
function regexParseNewZhengfang(html) {
    var courses = [];
    var tdRegex = /<td[^>]*\bid\s*=\s*["']?(\d+)-(\d+)["']?[^>]*>([\s\S]*?)<\/td>/gi;
    var match;

    while ((match = tdRegex.exec(html)) !== null) {
        var day = parseInt(match[1]);
        if (day < 1 || day > 7) continue;
        var cellContent = match[3];

        var blocks = cellContent.split(/<div\s+class=["']?timetable_con/i);
        for (var i = 1; i < blocks.length; i++) {
            var blockHtml = '<div class="timetable_con' + blocks[i];

            var name = "";
            var teacher = "";
            var location = "";
            var weeksStr = "";
            var sectionsStr = "";

            // Extract name from .title element
            var titleMatch = /<([a-zA-Z]+)[^>]*class=["']?title[^>]*>([\s\S]*?)<\/\1>/i.exec(blockHtml);
            if (titleMatch) {
                name = titleMatch[2].replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim();
            } else {
                var altMatch = /<u[^>]*class=["']?title[^>]*>([\s\S]*?)<\/u>/i.exec(blockHtml);
                if (altMatch) name = altMatch[1].replace(/<[^>]*>/g, '').trim();
            }

            // Extract teacher from title="教师" or title="老师" span/font
            var teacherMatch = /<span[^>]*title\s*=\s*["']?\s*(?:教师|老师)\s*["']?[^>]*>([\s\S]*?)<\/span>/i.exec(blockHtml);
            if (teacherMatch) {
                teacher = teacherMatch[1].replace(/<[^>]*>/g, '').trim();
            }
            if (!teacher) {
                teacherMatch = /title\s*=\s*["']?\s*(?:教师|老师)\s*["']?[^>]*>[\s\S]*?<\/span>\s*<font[^>]*>([\s\S]*?)<\/font>/i.exec(blockHtml);
                if (teacherMatch) teacher = teacherMatch[1].replace(/<[^>]*>/g, '').trim();
            }

            // Extract location
            var locMatch = /<span[^>]*title\s*=\s*["']?\s*(?:上课地点|教室)\s*["']?[^>]*>([\s\S]*?)<\/span>/i.exec(blockHtml);
            if (locMatch) {
                location = locMatch[1].replace(/<[^>]*>/g, '').trim();
            }
            if (!location) {
                locMatch = /title\s*=\s*["']?\s*(?:上课地点|教室)\s*["']?[^>]*>[\s\S]*?<\/span>\s*<font[^>]*>([\s\S]*?)<\/font>/i.exec(blockHtml);
                if (locMatch) location = locMatch[1].replace(/<[^>]*>/g, '').trim();
            }

            // Extract time info from title="节/周"
            var timeMatch = /<span[^>]*title\s*=\s*["']?节\/周\s*["']?[^>]*>([\s\S]*?)<\/span>/i.exec(blockHtml);
            if (timeMatch) {
                var timeText = timeMatch[1].replace(/<[^>]*>/g, '').trim();
                var secMatch = /(\d+)\s*[-至~～—－]\s*(\d+)\s*节/.exec(timeText);
                if (secMatch) sectionsStr = secMatch[1] + '-' + secMatch[2] + '节';
                var weekMatch = /(\d+\s*[-至~～—－]\s*\d+\s*周[^\s]*)/.exec(timeText);
                if (weekMatch) weeksStr = weekMatch[1];
            }

            // Fallback: pattern (X-X节) X周
            var rawMatch = /[\(（](\d+(?:-\d+)?节)[\)）]\s*([^<]*周[^<]*)/i.exec(blockHtml);
            if (rawMatch) {
                sectionsStr = rawMatch[1];
                weeksStr = rawMatch[2];
            }

            if (!name || !weeksStr || !sectionsStr) continue;

            var weeks = parseWeeksOld(weeksStr);
            var sections = parseSectionsOld(sectionsStr);
            if (weeks.length === 0 || sections.length === 0) continue;

            courses.push({
                name: name,
                teacher: teacher || '',
                position: location || '',
                day: day,
                weeks: weeks,
                startSection: sections[0],
                endSection: sections[sections.length - 1]
            });
        }
    }

    // Also try list format
    if (courses.length === 0) {
        var listRegex = /<tr[^>]*>\s*<td[^>]*id=["']?jc_(\d+)-(\d+)-(\d+)["']?[^>]*>\s*<\/td>\s*<td[^>]*>([\s\S]*?)<\/td>\s*<\/tr>/gi;
        var listMatch;
        while ((listMatch = listRegex.exec(html)) !== null) {
            var listDay = parseInt(listMatch[1]);
            var secStart = parseInt(listMatch[2]);
            var secEnd = parseInt(listMatch[3]);
            var listBlock = listMatch[4];

            var listName = "";
            var titleM = /<([a-zA-Z]+)[^>]*class=["']?title[^>]*>([\s\S]*?)<\/\1>/i.exec(listBlock);
            if (titleM) listName = titleM[2].replace(/<[^>]*>/g, '').trim();

            var listTeacher = "";
            var listTeacherM = /<span[^>]*title\s*=\s*["']?\s*(?:教师|老师)\s*["']?[^>]*>([\s\S]*?)<\/span>/i.exec(listBlock);
            if (listTeacherM) listTeacher = listTeacherM[1].replace(/<[^>]*>/g, '').trim();

            var listLoc = "";
            var listLocM = /<span[^>]*title\s*=\s*["']?\s*(?:上课地点|教室)\s*["']?[^>]*>([\s\S]*?)<\/span>/i.exec(listBlock);
            if (listLocM) listLoc = listLocM[1].replace(/<[^>]*>/g, '').trim();

            var weekMatch2 = /(\d+\s*[-至~～—－]\s*\d+\s*周[^\s]*)/i.exec(listBlock);
            var listWeeks = weekMatch2 ? parseWeeksOld(weekMatch2[1]) : [];

            if (listName && listWeeks.length > 0) {
                courses.push({
                    name: listName,
                    teacher: listTeacher || '',
                    position: listLoc || '',
                    day: listDay,
                    weeks: listWeeks,
                    startSection: secStart,
                    endSection: secEnd
                });
            }
        }
    }

    return courses;
}

function parseWeeksOld(str) {
    var weeks = [];
    if (!str) return weeks;
    var type = 0;
    if (str.indexOf('单') > -1) type = 1;
    if (str.indexOf('双') > -1) type = 2;
    str = str.replace(/周数[:：]/g, '').replace(/共\d+周|共\d+次|共\d+节/g, '');
    str = str.replace(/[至~～—－]/g, '-').replace(/周|单|双|\(|\)|（|）/g, '');
    var parts = str.split(/[,，;、]/);
    for (var pi = 0; pi < parts.length; pi++) {
        var part = parts[pi].trim();
        if (part.indexOf('-') > -1) {
            var range = part.split('-');
            var start = parseInt(range[0]);
            var end = parseInt(range[1]);
            if (!isNaN(start) && !isNaN(end)) {
                for (var w = start; w <= end; w++) {
                    if (type === 0 || (type === 1 && w % 2 !== 0) || (type === 2 && w % 2 === 0)) {
                        weeks.push(w);
                    }
                }
            }
        } else if (part !== '') {
            var week = parseInt(part);
            if (!isNaN(week)) {
                if (type === 0 || (type === 1 && week % 2 !== 0) || (type === 2 && week % 2 === 0)) {
                    weeks.push(week);
                }
            }
        }
    }
    return weeks.sort(function(a, b) { return a - b; });
}

function parseSectionsOld(str) {
    var sections = [];
    var s = str.replace(/第/g, '').replace(/节次[:：]/g, '').replace(/节/g, '').replace(/[\(（\)）]/g, '');
    s = s.replace(/[至~～—－]/g, '-');
    var parts = s.split('-');
    var start = parseInt(parts[0]);
    var end = parseInt(parts[1] || parts[0]);
    if (!isNaN(start)) {
        for (var sec = start; sec <= end; sec++) sections.push(sec);
    }
    return sections;
}

function regexScrapeAndParse() {
    console.log('JS: 使用正则 HTML 解析器 (无需 jQuery)');
    var html = document.documentElement.outerHTML;
    var courses = regexParseNewZhengfang(html);
    console.log('JS: 正则解析完成，共 ' + courses.length + ' 门课程');
    if (courses.length === 0) return null;
    return { courses: courses };
}

async function runImportFlow() {
    var alertConfirmed = await window.AndroidBridgePromise.showAlert(
        "教务系统课表导入",
        "导入前请确保您已在浏览器中成功登录教务系统，并处于课表查询页面且已点击查询。",
        "好的，开始导入"
    );
    if (!alertConfirmed) {
        AndroidBridge.showToast("用户取消了导入。");
        return;
    }

    var result = null;

    // Try jQuery-based DOM scraping first (more accurate for dynamic content)
    if (typeof window.jQuery !== 'undefined' || typeof $ !== 'undefined') {
        console.log('JS: jQuery 可用，使用 DOM 解析');
        result = await scrapeAndParseCourses();
    }

    // Fallback to regex-based HTML parsing (no jQuery needed)
    if (result === null) {
        console.log('JS: jQuery 不可用或 DOM 解析失败，使用正则 HTML 解析');
        result = regexScrapeAndParse();
    }

    if (result === null) {
        console.log("JS: 课程获取或解析失败，流程终止。");
        AndroidBridge.showToast("未找到课程数据，请确认已登录并处于课表查询页面。");
        return;
    }
    var courses = result.courses;

    var saveResult = await saveCourses(courses);
    if (!saveResult) {
        console.log("JS: 课程保存失败，流程终止。");
        return;
    }

    console.log("JS: 整个导入流程执行完毕并成功。共 " + courses.length + " 门课程");
    AndroidBridge.notifyTaskCompletion();
}

runImportFlow();
