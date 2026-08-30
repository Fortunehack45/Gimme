let currentTab = 'download';

function switchTab(tab) {
    currentTab = tab;
    document.getElementById('tabDownloadBtn').classList.toggle('active', tab === 'download');
    document.getElementById('tabUploadBtn').classList.toggle('active', tab === 'upload');
    document.getElementById('downloadSection').classList.toggle('active', tab === 'download');
    document.getElementById('uploadSection').classList.toggle('active', tab === 'upload');

    if (tab === 'download') {
        loadFiles();
    }
}

async function checkStatus() {
    try {
        const res = await fetch('/api/status');
        if (res.ok) {
            const data = await res.json();
            document.getElementById('hostNameLabel').innerText = data.hostDeviceName || 'AirShare Phone';
        }
    } catch (e) {
        console.error('Status fetch error:', e);
    }
}

async function loadFiles() {
    const container = document.getElementById('fileListContainer');
    try {
        const res = await fetch('/api/web/files');
        if (res.ok) {
            const data = await res.json();
            if (data.hostName) {
                document.getElementById('hostNameLabel').innerText = data.hostName;
            }
            if (!data.files || data.files.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-icon">📁</div>
                        <p>No files shared in this session yet</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = data.files.map(file => {
                const icon = getCategoryIcon(file.category);
                return `
                    <div class="file-item">
                        <div class="file-info">
                            <div class="file-icon">${icon}</div>
                            <div class="file-details">
                                <h4>${escapeHtml(file.name)}</h4>
                                <span>${file.formattedSize || formatSize(file.size)}</span>
                            </div>
                        </div>
                        <a class="btn-download" href="/api/web/download?id=${file.id}" download="${file.name}">
                            Download
                        </a>
                    </div>
                `;
            }).join('');
        }
    } catch (e) {
        console.error('Failed to load files:', e);
    }
}

function getCategoryIcon(category) {
    switch (category) {
        case 'PHOTOS': return '🖼️';
        case 'VIDEOS': return '🎬';
        case 'MUSIC': return '🎵';
        case 'APPS': return '📦';
        case 'DOCS': return '📄';
        default: return '📁';
    }
}

function formatSize(bytes) {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

function escapeHtml(text) {
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Drag and drop upload setup
const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');

['dragenter', 'dragover'].forEach(name => {
    dropZone.addEventListener(name, (e) => {
        e.preventDefault();
        dropZone.classList.add('dragover');
    });
});

['dragleave', 'drop'].forEach(name => {
    dropZone.addEventListener(name, (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
    });
});

dropZone.addEventListener('drop', (e) => {
    const files = e.dataTransfer.files;
    if (files.length > 0) {
        uploadFiles(files);
    }
});

fileInput.addEventListener('change', (e) => {
    if (fileInput.files.length > 0) {
        uploadFiles(fileInput.files);
    }
});

function uploadFiles(files) {
    const progressCard = document.getElementById('uploadProgressContainer');
    const nameLabel = document.getElementById('uploadFileName');
    const percentLabel = document.getElementById('uploadPercentage');
    const barFill = document.getElementById('uploadBarFill');

    progressCard.style.display = 'block';

    Array.from(files).forEach(file => {
        nameLabel.innerText = `Uploading: ${file.name}`;
        const formData = new FormData();
        formData.append('file', file, file.name);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/web/upload', true);

        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable) {
                const percent = Math.round((e.loaded / e.total) * 100);
                percentLabel.innerText = `${percent}%`;
                barFill.style.width = `${percent}%`;
            }
        };

        xhr.onload = () => {
            if (xhr.status === 200) {
                nameLabel.innerText = `✅ Sent ${file.name} to phone!`;
                percentLabel.innerText = '100%';
                setTimeout(() => {
                    progressCard.style.display = 'none';
                    barFill.style.width = '0%';
                }, 3000);
            } else {
                nameLabel.innerText = `❌ Failed to upload ${file.name}`;
            }
        };

        xhr.onerror = () => {
            nameLabel.innerText = `❌ Error sending ${file.name}`;
        };

        xhr.send(formData);
    });
}

// Initial load
checkStatus();
loadFiles();
setInterval(checkStatus, 5000);
