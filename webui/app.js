/**
 * BIM Designer — Web UI Application
 *
 * HTTP API client (POST /api) + tab routing.
 * No WebSocket — uses fetch() for reliability.
 *
 * // Implementing BIM_Designer_UserGuide.md §13
 */

const BIM = (() => {
    'use strict';

    let currentBuilding = '';
    let buildingDb = {};
    let selectedOrderLineId = 0;
    let bomProducts = [];
    let currentOrderLines = [];

    // ── HTTP API ────────────────────────────────────────────

    function send(action, params) {
        const body = Object.assign({ action: action }, params || {});
        return fetch('/api', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
        .then(resp => {
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            return resp.json();
        })
        .then(data => {
            setStatus('connected');
            return data;
        })
        .catch(err => {
            setStatus('disconnected');
            throw err;
        });
    }

    function setStatus(state) {
        const dot = document.getElementById('statusDot');
        const label = document.getElementById('statusLabel');
        const footer = document.getElementById('footerWs');
        dot.className = 'status-dot ' + state;
        label.textContent = state === 'connected' ? 'Online' : 'Offline';
        footer.textContent = 'API: ' + state;
    }

    // ── Tab Navigation ──────────────────────────────────────

    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                tabs.forEach(t => t.classList.remove('active'));
                document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
                tab.classList.add('active');
                const panel = document.getElementById('panel-' + tab.dataset.tab);
                if (panel) panel.classList.add('active');
                location.hash = 'tab=' + tab.dataset.tab;
            });
        });

        const hash = location.hash.replace('#', '');
        if (hash.startsWith('tab=')) {
            const tab = document.querySelector('.tab[data-tab="' + hash.split('=')[1] + '"]');
            if (tab) tab.click();
        }
    }

    // ── Building Selector ───────────────────────────────────

    function loadBuildings() {
        send('scanLibrary').then(renderBuildingSelector).catch(err => {
            console.error('scanLibrary failed:', err);
        });
    }

    function renderBuildingSelector(data) {
        const select = document.getElementById('buildingSelect');
        select.innerHTML = '<option value="">Select Building...</option>';

        const buildings = data.buildings || data.data || [];
        buildingDb = {};
        buildings.forEach(b => {
            const id = b.buildingId || b.code || '';
            buildingDb[id] = b;
            const opt = document.createElement('option');
            opt.value = id;
            opt.textContent = (b.name || b.code || id) +
                (b.elementCount ? ' (' + b.elementCount + ' el)' : '') +
                '  [' + (b.code || '') + ']';
            select.appendChild(opt);
        });

        // Update 3D Federation
        renderFederationBuildings(buildings);
        const fedDir = document.getElementById('fedLibraryDir');
        if (fedDir) fedDir.textContent = data.libraryDir || '';

        select.onchange = () => {
            currentBuilding = select.value;
            document.getElementById('footerBuilding').textContent =
                currentBuilding || 'No building selected';
            const info = buildingDb[currentBuilding];
            const dbPathEl = document.getElementById('fedDbPath');
            if (dbPathEl && info) dbPathEl.textContent = info.dbFile || '';
            if (currentBuilding) onBuildingSelected();
        };
    }

    function onBuildingSelected() {
        loadBomTree();
        loadOrderLines();
        scanBomProducts();
    }

    // ── 1D: Order Configurator (§29.5) ───────────────────────

    function scanBomProducts() {
        send('scanBomProducts').then(data => {
            bomProducts = data.products || [];
            const select = document.getElementById('bomProductSelect');
            select.innerHTML = '<option value="">Select BOM Template...</option>';
            bomProducts.forEach(p => {
                const opt = document.createElement('option');
                opt.value = p.bomId;
                opt.textContent = esc(p.name || p.bomId) +
                    ' [' + esc(p.source) + '] (' + (p.elementCount || 0) + ' lines)';
                select.appendChild(opt);
            });
        }).catch(() => {});
    }

    function loadBomTree() {
        if (!currentBuilding) return;
        send('getBomTree', { buildingId: currentBuilding }).then(renderBomTree).catch(() => {});
    }

    function renderBomTree(data) {
        const container = document.getElementById('bomTree');
        const tree = data.tree || data.children || data;
        if (!tree || (Array.isArray(tree) && tree.length === 0)) {
            container.innerHTML = '<div class="placeholder"><div class="icon">&#9776;</div>' +
                '<h3>Empty BOM</h3><p>Select a BOM template and click BOM Drop.</p></div>';
            document.getElementById('bomCount').textContent = '0 items';
            return;
        }
        const nodes = Array.isArray(tree) ? tree : [tree];
        container.innerHTML = nodes.map(n => renderNode(n)).join('');
        document.getElementById('bomCount').textContent = countNodes(nodes) + ' items';
    }

    function renderNode(node) {
        const name = node.name || node.bomId || node.familyRef || node.productId || '?';
        const type = node.type || node.hostType || node.bomType || '';
        const qty = node.qty !== undefined ? node.qty : '';
        const icon = getNodeIcon(type);
        const children = node.children || [];
        const lineId = node.orderLineId || 0;

        if (children.length === 0) {
            return '<div class="leaf" data-line-id="' + lineId + '" onclick="BIM.selectLine(' + lineId + ')">' +
                '<span class="node-icon">' + icon + '</span>' +
                '<span class="node-name">' + esc(name) + '</span>' +
                (qty ? '<span class="node-qty">' + qty + '</span>' : '') +
                (type ? '<span class="node-type">' + esc(type) + '</span>' : '') +
                '</div>';
        }
        return '<details open><summary data-line-id="' + lineId + '" onclick="BIM.selectLine(' + lineId + ')">' +
            '<span class="node-icon">' + icon + '</span>' +
            '<span class="node-name">' + esc(name) + '</span>' +
            (qty ? '<span class="node-qty">' + qty + '</span>' : '') +
            (type ? '<span class="node-type">' + esc(type) + '</span>' : '') +
            '</summary>' + children.map(c => renderNode(c)).join('') + '</details>';
    }

    function countNodes(nodes) {
        let c = 0;
        nodes.forEach(n => { c++; if (n.children) c += countNodes(n.children); });
        return c;
    }

    function getNodeIcon(type) {
        const icons = { 'BUILDING':'&#127970;', 'FLOOR':'&#9632;', 'ROOM':'&#9633;',
                        'SET':'&#9654;', 'ITEM':'&#9679;', 'LEAF':'&#9679;', 'COMPONENT':'&#9675;' };
        return icons[(type || '').toUpperCase()] || '&#8226;';
    }

    function loadOrderLines() {
        if (!currentBuilding) return;
        send('listOrderLines', { buildingId: currentBuilding }).then(data => {
            currentOrderLines = data.lines || data.orderLines || [];
            renderOrderLines(currentOrderLines);
        }).catch(() => {});
    }

    function renderOrderLines(lines) {
        const count = document.getElementById('orderLineCount');
        if (count) count.textContent = lines.length + ' lines';
        document.getElementById('orderLinesBody').innerHTML = lines.map((l, i) => {
            const lineId = l.orderLineId || l.lineId || (i+1);
            const status = (l.validationStatus || l.status || 'DR').toUpperCase();
            const statusClass = status === 'KICKBACK' ? 'kickback' : status.toLowerCase().slice(0,2);
            const sel = lineId === selectedOrderLineId ? ' selected' : '';
            return '<tr class="order-line-row' + sel + '" data-line-id="' + lineId +
                '" onclick="BIM.selectLine(' + lineId + ')">' +
                '<td>' + (i+1) + '</td>' +
                '<td>' + esc(l.familyRef || l.productId || '') + '</td>' +
                '<td>' + esc(l.hostType || '') + '</td>' +
                '<td>' + esc(l.bomCategory || l.category || '') + '</td>' +
                '<td class="num editable" ondblclick="BIM.editCell(this,' + lineId + ',\'widthMm\')">' +
                    fmt(l.widthMm || 0) + '</td>' +
                '<td class="num editable" ondblclick="BIM.editCell(this,' + lineId + ',\'depthMm\')">' +
                    fmt(l.depthMm || 0) + '</td>' +
                '<td class="num editable" ondblclick="BIM.editCell(this,' + lineId + ',\'heightMm\')">' +
                    fmt(l.heightMm || 0) + '</td>' +
                '<td class="num editable" ondblclick="BIM.editCell(this,' + lineId + ',\'qty\')">' +
                    (l.qty || 1) + '</td>' +
                '<td><span class="status-badge ' + statusClass + '">' + esc(status) + '</span></td>' +
                '</tr>';
        }).join('');
    }

    function selectLine(lineId) {
        selectedOrderLineId = lineId;
        // Highlight selected row
        document.querySelectorAll('.order-line-row').forEach(r => r.classList.remove('selected'));
        const row = document.querySelector('.order-line-row[data-line-id="' + lineId + '"]');
        if (row) row.classList.add('selected');
        // Load ASI for this line
        loadASI(lineId);
    }

    function loadASI(lineId) {
        if (!currentBuilding || !lineId) return;
        const ref = document.getElementById('asiLineRef');
        ref.textContent = 'Line #' + lineId;
        send('readASI', { buildingId: currentBuilding, orderLineId: lineId }).then(data => {
            renderASI(data, lineId);
        }).catch(() => {
            document.getElementById('asiPanel').innerHTML =
                '<div style="padding:12px;color:var(--text-dim)">Failed to load ASI.</div>';
        });
    }

    function renderASI(data, lineId) {
        const panel = document.getElementById('asiPanel');
        if (!data.success && data.error) {
            panel.innerHTML = '<div style="padding:12px;color:var(--danger)">' + esc(data.error) + '</div>';
            return;
        }

        const fields = data.fields || [];
        const setName = data.attributeSetName || '';
        const isInstance = data.isInstanceAttribute;

        if (fields.length === 0 && !setName) {
            panel.innerHTML = '<div style="padding:12px;color:var(--text-dim)">' +
                'No attribute set defined for this product.</div>';
            return;
        }

        let html = '';
        if (setName) {
            html += '<div style="font-size:11px;color:var(--text-dim);margin-bottom:8px">' +
                'Set: <strong>' + esc(setName) + '</strong>' +
                (isInstance ? ' <span style="color:var(--accent)">(instance)</span>' : ' (shared)') +
                '</div>';
        }

        if (fields.length === 0) {
            html += '<div style="padding:8px 0;color:var(--text-dim)">' +
                'No values set. Edit dimensions below or use auto-populate.</div>';
            // Show default dimension fields for instance attributes
            if (isInstance) {
                html += renderASIForm([
                    { name: 'width_mm', value: '', valueType: 'NUMBER', overridden: false },
                    { name: 'depth_mm', value: '', valueType: 'NUMBER', overridden: false },
                    { name: 'height_mm', value: '', valueType: 'NUMBER', overridden: false },
                    { name: 'material', value: '', valueType: 'TEXT', overridden: false },
                    { name: 'finish', value: '', valueType: 'TEXT', overridden: false }
                ], lineId);
            }
        } else {
            html += renderASIForm(fields, lineId);
        }

        panel.innerHTML = '<div class="asi-form">' + html + '</div>';
    }

    function renderASIForm(fields, lineId) {
        return fields.map(f => {
            const cls = f.overridden ? ' overridden' : '';
            return '<div class="asi-field' + cls + '">' +
                '<label>' + esc(f.name) + '</label>' +
                '<input type="' + (f.valueType === 'NUMBER' ? 'number' : 'text') + '"' +
                ' value="' + esc(f.value || '') + '"' +
                ' data-field="' + esc(f.name) + '"' +
                ' data-line-id="' + lineId + '"' +
                ' onchange="BIM.onASIChange(this)"' +
                ' placeholder="' + esc(f.name) + '">' +
                (f.overridden ? '<span class="asi-hint">User override</span>' : '') +
                '</div>';
        }).join('');
    }

    function onASIChange(input) {
        const field = input.dataset.field;
        const lineId = parseInt(input.dataset.lineId, 10);
        const value = input.value;
        if (!currentBuilding || !lineId || !field) return;
        send('updateASI', {
            buildingId: currentBuilding,
            orderLineId: lineId,
            attributeName: field,
            value: value
        }).then(data => {
            if (data.success) {
                renderASI(data, lineId);
            }
        }).catch(() => {});
    }

    function editCell(td, lineId, field) {
        if (td.querySelector('input')) return; // already editing
        const currentVal = td.textContent.trim().replace(/,/g, '');
        const input = document.createElement('input');
        input.type = 'number';
        input.value = currentVal;
        input.style.width = '100%';
        td.textContent = '';
        td.appendChild(input);
        input.focus();
        input.select();

        function commit() {
            const newVal = input.value.trim();
            if (newVal !== currentVal && newVal !== '') {
                send('updateOrderLine', {
                    buildingId: currentBuilding,
                    orderLineId: lineId,
                    field: field,
                    value: newVal
                }).then(() => {
                    loadOrderLines();
                }).catch(() => {
                    td.textContent = currentVal;
                });
            } else {
                td.textContent = currentVal;
            }
        }

        input.addEventListener('blur', commit);
        input.addEventListener('keydown', e => {
            if (e.key === 'Enter') { e.preventDefault(); commit(); }
            if (e.key === 'Escape') { td.textContent = currentVal; }
        });
    }

    function bomDrop() {
        const select = document.getElementById('bomProductSelect');
        let id = select.value;
        if (!id) {
            id = currentBuilding;
        }
        if (!id) {
            alert('Select a BOM template from the dropdown first.');
            return;
        }
        document.getElementById('docStatus').textContent = 'Dropping...';
        send('bomDrop', { buildingProductId: id }).then(data => {
            if (!data.success) {
                document.getElementById('docStatus').textContent = 'Drop failed';
                alert('BOM Drop failed: ' + (data.error || 'Unknown error'));
                return;
            }
            currentBuilding = data.orderId || currentBuilding;
            document.getElementById('docStatus').textContent =
                'Dropped (' + (data.totalElements || 0) + ' elements)';
            // Render returned tree directly
            if (data.tree) {
                renderBomTree({ tree: data.tree });
            }
            loadOrderLines();
        }).catch(e => {
            document.getElementById('docStatus').textContent = 'Drop failed';
            alert('BOM Drop failed: ' + e.message);
        });
    }

    function save() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        document.getElementById('docStatus').textContent = 'Saving...';
        send('save', { buildingId: currentBuilding, bboxes: [],
            variantLabel: 'WebUI-' + new Date().toISOString().slice(0,16)
        }).then(() => {
            document.getElementById('docStatus').textContent = 'Saved (IP)';
        }).catch(e => alert('Save failed: ' + e.message));
    }

    function completeOrder() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        document.getElementById('docStatus').textContent = 'Validating...';
        // prepareIt → validate, then completeIt → compile
        send('prepareIt', { buildingId: currentBuilding }).then(data => {
            if (data.success === false) {
                document.getElementById('docStatus').textContent = 'Kickback';
                loadOrderLines(); // reload to show kickback status
                return;
            }
            document.getElementById('docStatus').textContent = 'Compiling...';
            send('completeIt', { buildingId: currentBuilding }).then(cdata => {
                document.getElementById('docStatus').textContent =
                    'Complete (CO) — ' + (cdata.elementCount || '?') + ' elements';
                loadBomTree();
                loadOrderLines();
            }).catch(e => {
                document.getElementById('docStatus').textContent = 'Compile failed';
                alert('Complete failed: ' + e.message);
            });
        }).catch(e => {
            document.getElementById('docStatus').textContent = 'Validation failed';
            alert('Prepare failed: ' + e.message);
        });
    }

    function compile() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        document.getElementById('docStatus').textContent = 'Compiling...';
        send('compile', { buildingId: currentBuilding }).then(data => {
            document.getElementById('docStatus').textContent =
                'Compiled: ' + (data.elementCount || '?') + ' elements';
        }).catch(e => alert('Compile failed: ' + e.message));
    }

    // ── 3D: Federation ──────────────────────────────────────

    function renderFederationBuildings(buildings) {
        const grid = document.getElementById('fedGrid');
        if (!grid) return;
        const total = buildings.reduce((s, b) => s + (b.elementCount || 0), 0);
        grid.innerHTML =
            '<div class="status-card"><div class="label">Buildings</div><div class="value">' + buildings.length + '</div></div>' +
            '<div class="status-card"><div class="label">Total Elements</div><div class="value">' + fmt(total) + '</div></div>' +
            '<div class="status-card"><div class="label">Library</div><div class="value" style="font-size:14px" id="fedLibraryDir">-</div></div>';

        const el = document.getElementById('fedBuildingCount');
        if (el) el.textContent = buildings.length;

        const body = document.getElementById('buildingsBody');
        if (!body) return;
        body.innerHTML = buildings.map(b => {
            const id = b.buildingId || b.code || '';
            return '<tr><td><strong>' + esc(b.code || '') + '</strong></td>' +
                '<td>' + esc(b.name || '-') + '</td>' +
                '<td class="num">' + (b.elementCount || '-') + '</td>' +
                '<td>' + esc(b.dbName || '-') + '</td>' +
                '<td><button class="btn" onclick="BIM.selectBuilding(\'' + esc(id) + '\')">Select</button>' +
                ' <button class="btn" onclick="BIM.loadDetail(\'' + esc(b.dbFile || '') + '\')">Detail</button></td></tr>';
        }).join('');
    }

    function selectBuilding(id) {
        currentBuilding = id;
        document.getElementById('buildingSelect').value = id;
        document.getElementById('footerBuilding').textContent = id;
        const info = buildingDb[id];
        const dbPathEl = document.getElementById('fedDbPath');
        if (dbPathEl && info) dbPathEl.textContent = info.dbFile || '';
        onBuildingSelected();
        document.querySelector('.tab[data-tab="1d"]').click();
    }

    function loadDetail(dbFile) {
        if (!dbFile) return;
        send('loadBuildingDetail', { dbFile: dbFile }).then(data => {
            let msg = (data.name || '') + '\n';
            msg += 'Elements: ' + (data.elementCount || '?') + '\n';
            if (data.bomStats) msg += 'BOMs: ' + Object.entries(data.bomStats).map(([k,v]) => k+'='+v).join(', ') + '\n';
            if (data.bomLineCount !== undefined) msg += 'BOM Lines: ' + data.bomLineCount;
            alert(msg);
        }).catch(e => alert('Failed: ' + e.message));
    }

    // ── 4D-7D: Data tabs ────────────────────────────────────

    function loadSchedule() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        send('constructionSchedule', { buildingId: currentBuilding,
            projectStartDate: document.getElementById('scheduleStart').value
        }).then(data => {
            const tasks = data.tasks || [];
            document.getElementById('scheduleBody').innerHTML = tasks.map(t =>
                '<tr><td>' + esc(t.name||t.task||'') + '</td><td>' + esc(t.discipline||'') +
                '</td><td>' + esc(t.startDate||'') + '</td><td>' + esc(t.endDate||'') +
                '</td><td class="num">' + (t.durationDays||'') +
                '</td><td>' + esc((t.dependencies||[]).join(', ')) + '</td></tr>'
            ).join('');
        }).catch(() => {});
    }

    function loadCost() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        send('costBreakdown', { buildingId: currentBuilding }).then(data => {
            const items = data.items || data.lines || [];
            let tM=0, tL=0, tE=0;
            document.getElementById('costBody').innerHTML = items.map(it => {
                const m=it.materialCost||0, l=it.labourCost||0, e=it.equipmentCost||0;
                tM+=m; tL+=l; tE+=e;
                return '<tr><td>'+esc(it.name||it.item||'')+'</td><td>'+esc(it.category||'')+
                    '</td><td class="num">'+fmt(m)+'</td><td class="num">'+fmt(l)+
                    '</td><td class="num">'+fmt(e)+'</td><td class="num">'+fmt(m+l+e)+'</td></tr>';
            }).join('') +
            '<tr class="total"><td colspan="2">Total</td><td class="num">'+fmt(tM)+'</td><td class="num">'+
            fmt(tL)+'</td><td class="num">'+fmt(tE)+'</td><td class="num">'+fmt(tM+tL+tE)+'</td></tr>';
        }).catch(() => {});
    }

    function loadCarbon() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        send('carbonFootprint', { buildingId: currentBuilding }).then(data => {
            const items = data.items || data.elements || [];
            const tCO2 = items.reduce((s,it) => s+(it.co2eKg||0), 0);
            const tMass = items.reduce((s,it) => s+(it.massKg||0), 0);
            document.getElementById('carbonSummary').innerHTML =
                '<div class="status-card"><div class="label">Total CO2e</div><div class="value">'+fmt(tCO2)+' kg</div></div>' +
                '<div class="status-card"><div class="label">Total Mass</div><div class="value">'+fmt(tMass)+' kg</div></div>' +
                '<div class="status-card"><div class="label">Intensity</div><div class="value">'+(tMass>0?(tCO2/tMass).toFixed(2):'-')+'</div></div>';
            document.getElementById('carbonBody').innerHTML = items.map(it =>
                '<tr><td>'+esc(it.name||it.element||'')+'</td><td>'+esc(it.material||'')+
                '</td><td class="num">'+fmt(it.massKg||0)+'</td><td class="num">'+fmt(it.co2eKg||0)+'</td></tr>'
            ).join('');
        }).catch(() => {});
    }

    function loadMaintenance() {
        if (!currentBuilding) { alert('Select a building first'); return; }
        send('maintenanceSchedule', { buildingId: currentBuilding }).then(data => {
            const items = data.items || data.assets || [];
            document.getElementById('maintenanceBody').innerHTML = items.map(it =>
                '<tr><td>'+esc(it.name||it.asset||'')+'</td><td>'+esc(it.type||it.assetType||'')+
                '</td><td class="num">'+(it.intervalYears||'')+'</td><td class="num">'+fmt(it.costPerEvent||0)+
                '</td><td class="num">'+fmt(it.lifecycleCost||0)+'</td></tr>'
            ).join('');
        }).catch(() => {});
    }

    // ── 8: Reports ──────────────────────────────────────────

    function loadPortfolio() {
        send('portfolio').then(data => {
            const projects = data.projects || [];
            document.getElementById('portfolioBody').innerHTML = projects.map(p =>
                '<tr><td>'+esc(p.name||p.buildingId||'')+'</td><td>'+esc(p.status||'')+
                '</td><td class="num">'+(p.elementCount||'')+'</td><td class="num">'+fmt(p.totalCost||0)+
                '</td><td class="num">'+(p.score||'')+'</td></tr>'
            ).join('');
            send('balancedScorecard').then(bsc => {
                document.getElementById('bscGrid').innerHTML = (bsc.perspectives||[]).map(p =>
                    '<div class="status-card"><div class="label">'+esc(p.name||'')+'</div><div class="value">'+(p.score||'-')+'</div></div>'
                ).join('');
            }).catch(() => {});
        }).catch(() => {});
    }

    // ── 9: NLP Query + Product Search ───────────────────────

    function setQuery(text) {
        document.getElementById('queryInput').value = text;
        executeQuery();
    }

    function executeQuery() {
        const query = document.getElementById('queryInput').value.trim();
        if (!query) return;
        send('executeNlpQuery', { query: query, buildingId: currentBuilding }).then(data => {
            const rows = data.rows || [], columns = data.columns || [];
            document.getElementById('queryIntent').textContent = data.intent ? 'Intent: '+data.intent : '';
            document.getElementById('queryElapsed').textContent = rows.length+' rows';
            if (columns.length) {
                document.getElementById('queryResultsHead').innerHTML = '<tr>'+columns.map(c=>'<th>'+esc(c)+'</th>').join('')+'</tr>';
            }
            document.getElementById('queryResults').innerHTML = rows.map(row =>
                '<tr>'+(Array.isArray(row) ? row.map(v=>'<td>'+esc(v)+'</td>').join('') :
                columns.map(c=>'<td>'+esc(row[c]||'')+'</td>').join(''))+'</tr>'
            ).join('');
        }).catch(() => {
            document.getElementById('queryResults').innerHTML =
                '<tr><td style="color:var(--text-dim)">NLP Query requires federation database.</td></tr>';
        });
    }

    function clearQueryResults() {
        document.getElementById('queryResults').innerHTML = '';
        document.getElementById('queryResultsHead').innerHTML = '<tr><th>Results will appear here</th></tr>';
        document.getElementById('queryIntent').textContent = '';
        document.getElementById('queryElapsed').textContent = '';
        document.getElementById('queryInput').value = '';
    }

    function searchProducts() {
        const el = document.getElementById('productSearchInput');
        const query = el ? el.value.trim() : '';
        if (!query) return;
        send('browseItems', { search: query, limit: 50 }).then(data => {
            const items = data.items || data.products || [];
            document.getElementById('productResults').innerHTML = items.map(it =>
                '<tr><td>'+esc(it.name||it.productId||'')+'</td><td>'+esc(it.category||'')+
                '</td><td class="num">'+(it.widthMm||'')+'</td><td class="num">'+(it.depthMm||'')+
                '</td><td class="num">'+(it.heightMm||'')+'</td></tr>'
            ).join('');
        }).catch(() => {});
    }

    // ── SSE: Server-Sent Events (Bonsai → Web UI) ───────────

    let sseSource = null;
    let bonsaiSelection = {};

    function connectSSE() {
        if (sseSource) sseSource.close();
        sseSource = new EventSource('/events');

        sseSource.addEventListener('selectionChanged', e => {
            try {
                bonsaiSelection = JSON.parse(e.data);
                renderSyncSelection(bonsaiSelection);
            } catch (err) { console.warn('SSE parse error:', err); }
        });

        sseSource.addEventListener('compileComplete', e => {
            try {
                const data = JSON.parse(e.data);
                const ds = document.getElementById('docStatus');
                if (ds) ds.textContent = 'Compiled → Loading in Bonsai (' +
                    (data.elementCount || '?') + ' el)';
                const fb = document.getElementById('applyFeedback');
                if (fb) fb.textContent = 'Compile output sent to Bonsai: ' +
                    (data.outputDbPath || '');
            } catch (err) {}
        });

        sseSource.addEventListener('schemeApplied', e => {
            try {
                const cmd = JSON.parse(e.data);
                const fb = document.getElementById('applyFeedback');
                if (fb) fb.textContent = 'Scheme sent to Bonsai: ' + (cmd.schemeName || cmd.command);
            } catch (err) {}
        });

        sseSource.onopen = () => {
            const st = document.getElementById('syncStatus');
            if (st) st.innerHTML = '<span class="sync-dot live"></span>Live';
        };

        sseSource.onerror = () => {
            const st = document.getElementById('syncStatus');
            if (st) st.innerHTML = '<span class="sync-dot offline"></span>Reconnecting...';
        };
    }

    function renderSyncSelection(sel) {
        const container = document.getElementById('syncSelection');
        if (!container) return;

        if (!sel || !sel.objectName) {
            container.innerHTML = '<div class="placeholder" style="padding:20px 10px">' +
                '<div class="icon" style="font-size:28px">&#128065;</div>' +
                '<p>Select an object in Bonsai viewport.</p></div>';
            return;
        }

        const fields = [
            ['Object', sel.objectName],
            ['IFC Class', sel.ifcClass],
            ['GUID', sel.guid],
            ['Discipline', sel.discipline],
            ['Material', sel.material],
            ['Product', sel.productId],
            ['Host', sel.hostType]
        ].filter(f => f[1]);

        container.innerHTML = '<div class="sync-object active">' +
            fields.map(([label, value]) =>
                '<span class="sync-label">' + esc(label) + '</span>' +
                '<span class="sync-value">' + esc(value) + '</span>'
            ).join('') + '</div>';

        // Auto-switch to tab 10 if user has something selected
        // (only if not already on tab 10 or 1d)
        const activeTab = document.querySelector('.tab.active');
        if (activeTab && activeTab.dataset.tab !== '10' && activeTab.dataset.tab !== '1d') {
            // Don't auto-switch — user might be on another tab intentionally
        }
    }

    // ── 10: Color Studio (Bonsai sync) ────────────────────────

    function applySchemeToSelected() {
        if (!bonsaiSelection.objectName) {
            alert('No object selected in Bonsai. Select something in the 3D viewport first.');
            return;
        }
        const palette = document.getElementById('paletteSelect').value;
        const colors = PALETTES[palette] || PALETTES.realistic;
        send('applyScheme', {
            schemeName: palette,
            objectName: bonsaiSelection.objectName,
            guid: bonsaiSelection.guid || '',
            color: JSON.stringify(colors[0][1])  // first color as default
        }).then(() => {
            document.getElementById('applyFeedback').textContent =
                'Sent "' + palette + '" scheme to Bonsai for "' + bonsaiSelection.objectName + '"';
        }).catch(e => {
            document.getElementById('applyFeedback').textContent = 'Failed: ' + e.message;
        });
    }

    function applySchemeToAll() {
        const palette = document.getElementById('paletteSelect').value;
        send('applyScheme', {
            schemeName: palette,
            objectName: '*',
            guid: '*'
        }).then(() => {
            document.getElementById('applyFeedback').textContent =
                'Sent "' + palette + '" scheme to all objects in Bonsai';
        }).catch(e => {
            document.getElementById('applyFeedback').textContent = 'Failed: ' + e.message;
        });
    }

    function applyDisciplineColor(disc) {
        const discColors = {
            'ARC':'#8B9DC3', 'STR':'#D4A574', 'MEP':'#6BB5E0', 'ELEC':'#F5D76E',
            'PLB':'#5DADE2', 'FP':'#E74C3C', 'HVAC':'#48C9B0', 'CW':'#BB8FCE'
        };
        send('applyScheme', {
            schemeName: 'discipline',
            filterDiscipline: disc,
            color: discColors[disc] || '#888888',
            objectName: bonsaiSelection.objectName || '*'
        }).then(() => {
            document.getElementById('applyFeedback').textContent =
                'Applied ' + disc + ' color to Bonsai';
        }).catch(() => {});
    }

    function resetBonsaiColors() {
        send('applyScheme', {
            schemeName: 'reset',
            objectName: '*',
            guid: '*'
        }).then(() => {
            document.getElementById('applyFeedback').textContent = 'Reset all colors in Bonsai';
        }).catch(() => {});
    }

    function launchBonsai() {
        // Try to launch Blender/Bonsai via server command
        send('launchBonsai', {}).then(data => {
            if (data.success) {
                document.getElementById('applyFeedback').textContent = 'Bonsai launching...';
            } else {
                alert('Bonsai not found.\n\nEnsure ~/bin/bonsai is on your PATH.');
            }
        }).catch(() => {
            alert('Bonsai not found.\n\nRun: bonsai');
        });
    }

    const PALETTES = {
        realistic: [['Concrete Lt','#C8C0B8'],['Concrete Md','#A89E94'],['Concrete Dk','#7A7068'],
            ['Steel','#A8B4C0'],['Aluminum','#C0C8D0'],['Glass','#B8D4E8'],['Wood Pine','#D4A460'],
            ['Wood Oak','#8B6914'],['Brick','#B85C38'],['Insulation','#F0E060'],['Ground','#8B7355'],
            ['Grass','#6B8E23'],['Copper','#B87333'],['PVC White','#E8E0D8'],['Tile','#D4C4B0'],['Asphalt','#484848']],
        phase: [['Foundation','#8B4513'],['Structure','#CD853F'],['Envelope','#DAA520'],['Interior','#F4A460'],
            ['MEP Rough','#6495ED'],['Finishes','#98FB98'],['Phase 1','#FF6347'],['Phase 2','#FF8C00'],
            ['Phase 3','#FFD700'],['Phase 4','#32CD32'],['Temporary','#DDA0DD'],['Existing','#808080'],
            ['Demolish','#DC143C'],['New Work','#00CED1'],['Future','#9370DB'],['Complete','#228B22']],
        safety: [['Safe','#2ED573'],['Caution','#FFA502'],['Warning','#FF6348'],['Danger','#FF4757'],
            ['Inspected','#7BED9F'],['Not Inspected','#DFE6E9'],['Failed','#E74C3C'],['Restricted','#F39C12'],
            ['Emergency','#FF0000'],['Exit Route','#00B894'],['Fire Equip','#D63031'],['First Aid','#00CEC9'],
            ['PPE Required','#6C5CE7'],['Hazard Zone','#FDCB6E'],['Cleared','#55EFC4'],['Quarantine','#B33939']],
        discipline: [['ARC','#8B9DC3'],['STR','#D4A574'],['MEP','#6BB5E0'],['ELEC','#F5D76E'],
            ['PLB','#5DADE2'],['HVAC','#48C9B0'],['FP','#E74C3C'],['CW','#BB8FCE'],
            ['Civil','#95A5A6'],['Landscape','#27AE60'],['Interiors','#E67E22'],['Signage','#2ECC71'],
            ['Equipment','#3498DB'],['Furniture','#9B59B6'],['Technology','#1ABC9C'],['Security','#E74C3C']]
    };

    function selectPalette(name) {
        const colors = PALETTES[name] || PALETTES.realistic;
        document.getElementById('colorSwatches').innerHTML = colors.map(([label, hex]) =>
            '<div style="background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:8px;text-align:center">' +
            '<div style="width:100%;height:32px;border-radius:4px;background:'+hex+';margin-bottom:6px"></div>' +
            '<div style="font-size:11px;color:var(--text-dim)">'+label+'</div>' +
            '<div style="font-size:10px;font-family:var(--mono);color:var(--text-dim)">'+hex+'</div></div>'
        ).join('');
    }

    function filterDiscipline(disc) {
        document.getElementById('paletteSelect').value = 'discipline';
        selectPalette('discipline');
    }

    // ── Utilities ───────────────────────────────────────────

    function esc(s) {
        if (s == null) return '';
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function fmt(n) {
        if (n == null || isNaN(n)) return '-';
        return Number(n).toLocaleString('en', {minimumFractionDigits:0, maximumFractionDigits:2});
    }

    // ── Init ────────────────────────────────────────────────

    document.addEventListener('DOMContentLoaded', () => {
        initTabs();
        loadBuildings();
        scanBomProducts();
        selectPalette('realistic');
        connectSSE();
    });

    return {
        send, bomDrop, save, compile, completeOrder,
        loadBuildings, selectBuilding, loadDetail,
        selectLine, loadASI, onASIChange, editCell,
        loadSchedule, loadCost, loadCarbon, loadMaintenance, loadPortfolio,
        searchProducts, setQuery, executeQuery, clearQueryResults,
        selectPalette, filterDiscipline,
        applySchemeToSelected, applySchemeToAll, applyDisciplineColor, resetBonsaiColors,
        launchBonsai
    };
})();
