const { app, BrowserWindow, ipcMain, shell, Menu } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

let mainWindow;
let javaProcess;
let isJavaServerReady = false;
let apiKeys = {
    steamApiKey: '',
    geminiApiKey: ''
};

console.log('=== App Starting ===');
console.log('App path:', app.getAppPath());
console.log('Is packaged:', app.isPackaged);
console.log('Working directory:', process.cwd());

// 올바른 앱 루트 경로 얻기 (asar 문제 해결)
const getAppRootPath = () => {
    if (app.isPackaged) {
        // 빌드된 exe에서는 process.resourcesPath 사용
        return process.resourcesPath;
    } else {
        // 개발 환경에서는 process.cwd() 사용
        return process.cwd();
    }
};

console.log('App root path:', getAppRootPath());

// API 키 파일 경로
const getApiKeysPath = () => {
    const userDataPath = app.getPath('userData');
    return path.join(userDataPath, 'api-keys.json');
};

// API 키 로드
function loadApiKeys() {
    try {
        const apiKeysPath = getApiKeysPath();
        console.log('Looking for API keys at:', apiKeysPath);
        if (fs.existsSync(apiKeysPath)) {
            const data = fs.readFileSync(apiKeysPath, 'utf8');
            apiKeys = JSON.parse(data);
            console.log('API keys loaded');
            return true;
        } else {
            console.log('No API keys file found');
            return false;
        }
    } catch (error) {
        console.error('Failed to load API keys:', error);
        return false;
    }
}

// API 키 저장
function saveApiKeys(keys) {
    try {
        const apiKeysPath = getApiKeysPath();
        const userDataPath = path.dirname(apiKeysPath);

        if (!fs.existsSync(userDataPath)) {
            fs.mkdirSync(userDataPath, { recursive: true });
        }

        apiKeys = { ...apiKeys, ...keys };
        fs.writeFileSync(apiKeysPath, JSON.stringify(apiKeys, null, 2));
        console.log('API keys saved');
        return true;
    } catch (error) {
        console.error('Failed to save API keys:', error);
        throw error;
    }
}

// Steam API 키 유효성 검증
async function validateSteamApiKey(apiKey) {
    if (!apiKey || apiKey.length !== 32) {
        return { valid: false, message: 'Steam API key must be 32 characters long' };
    }

    try {
        const response = await fetch(`http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=${apiKey}&steamids=76561197960435530`, {
            signal: AbortSignal.timeout(5000)
        });

        if (response.ok) {
            const data = await response.json();
            if (data.response && data.response.players) {
                return { valid: true, message: 'Steam API key is valid' };
            }
        }

        return { valid: false, message: 'Invalid Steam API key or API error' };
    } catch (error) {
        console.error('Steam API validation error:', error);
        return { valid: false, message: 'Failed to validate Steam API key (network error)' };
    }
}

// Gemini API 키 유효성 검증
async function validateGeminiApiKey(apiKey) {
    if (!apiKey || apiKey.length < 20) {
        return { valid: false, message: 'Gemini API key appears to be too short' };
    }

    try {
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`, {
            signal: AbortSignal.timeout(5000)
        });

        if (response.ok) {
            return { valid: true, message: 'Gemini API key is valid' };
        } else if (response.status === 403) {
            return { valid: false, message: 'Invalid Gemini API key or access denied' };
        } else {
            return { valid: false, message: 'Failed to validate Gemini API key' };
        }
    } catch (error) {
        console.error('Gemini API validation error:', error);
        return { valid: false, message: 'Failed to validate Gemini API key (network error)' };
    }
}

// API 키 자동 검증 함수
async function validateAllApiKeys() {
    console.log('Starting API key validation...');

    const validationResults = {
        steam: { valid: false, message: '' },
        gemini: { valid: false, message: '' },
        hasKeys: false,
        allValid: false
    };

    // API 키가 존재하는지 확인
    if (!apiKeys.steamApiKey && !apiKeys.geminiApiKey) {
        console.log('No API keys found');
        validationResults.hasKeys = false;
        return validationResults;
    }

    validationResults.hasKeys = true;

    // Steam API 키 검증
    if (apiKeys.steamApiKey) {
        console.log('Validating Steam API key...');
        try {
            validationResults.steam = await validateSteamApiKey(apiKeys.steamApiKey);
            console.log(`Steam API validation result: ${validationResults.steam.valid ? 'VALID' : 'INVALID'} ${validationResults.steam.message}`);
        } catch (error) {
            console.error('Steam API validation failed:', error);
            validationResults.steam = { valid: false, message: 'Network error during validation' };
        }
    } else {
        validationResults.steam = { valid: false, message: 'Steam API key not provided' };
    }

    // Gemini API 키 검증
    if (apiKeys.geminiApiKey) {
        console.log('Validating Gemini API key...');
        try {
            validationResults.gemini = await validateGeminiApiKey(apiKeys.geminiApiKey);
            console.log(`Gemini API validation result: ${validationResults.gemini.valid ? 'VALID' : 'INVALID'} ${validationResults.gemini.message}`);
        } catch (error) {
            console.error('Gemini API validation failed:', error);
            validationResults.gemini = { valid: false, message: 'Network error during validation' };
        }
    } else {
        validationResults.gemini = { valid: false, message: 'Gemini API key not provided' };
    }

    // 전체 결과 확인
    validationResults.allValid = validationResults.steam.valid && validationResults.gemini.valid;

    console.log(`API validation complete. All valid: ${validationResults.allValid ? 'YES' : 'NO'}`);
    return validationResults;
}

// API 키 검증 결과 화면 표시
function showValidationResultScreen(validationResults) {
    try {
        const steamStatus = validationResults.steam.valid ?
            `<div style="color: #2CC934; margin-bottom: 10px;">VALID - Steam API: ${validationResults.steam.message}</div>` :
            `<div style="color: #f44336; margin-bottom: 10px;">INVALID - Steam API: ${validationResults.steam.message}</div>`;

        const geminiStatus = validationResults.gemini.valid ?
            `<div style="color: #2CC934; margin-bottom: 10px;">VALID - Gemini API: ${validationResults.gemini.message}</div>` :
            `<div style="color: #f44336; margin-bottom: 10px;">INVALID - Gemini API: ${validationResults.gemini.message}</div>`;

        const resultHtml = `
            <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                    <h2 style="text-align: center; margin-bottom: 30px;">API Key Validation Results</h2>
                    
                    <div style="margin-bottom: 25px; padding: 20px; background: rgba(255,255,255,0.1); border-radius: 10px;">
                        ${steamStatus}
                        ${geminiStatus}
                    </div>

                    ${validationResults.allValid ?
            `<div style="padding: 15px; background: rgba(76, 175, 80, 0.2); border: 1px solid #2CC934; border-radius: 8px; margin-bottom: 25px; text-align: center;">
                            <strong>All API keys are valid! Starting application...</strong>
                        </div>` :
            `<div style="padding: 15px; background: rgba(244, 67, 54, 0.2); border: 1px solid #f44336; border-radius: 8px; margin-bottom: 25px; text-align: center;">
                            <strong>Some API keys are invalid. Please update your configuration.</strong>
                        </div>`
        }

                    <div style="display: flex; gap: 10px; margin-top: 30px;">
                        ${validationResults.allValid ?
            `<button onclick="continueWithValidKeys()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #2CC934; color: white;">Continue</button>` :
            `<button onclick="reconfigureKeys()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #ff9800; color: white;">Reconfigure API Keys</button>
                             <button onclick="continueAnyway()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);">Continue Anyway</button>`
        }
                    </div>
                </div>
            </div>
            <script>
                const { ipcRenderer } = require('electron');
                
                function continueWithValidKeys() {
                    ipcRenderer.invoke('start-main-app');
                }
                
                function reconfigureKeys() {
                    ipcRenderer.invoke('show-config-screen');
                }
                
                function continueAnyway() {
                    ipcRenderer.invoke('start-main-app');
                }

                // 자동으로 계속 진행 (유효한 키가 있는 경우)
                ${validationResults.allValid ? 'setTimeout(continueWithValidKeys, 3000);' : ''}
            </script>
        `;

        mainWindow.loadURL(`data:text/html,${encodeURIComponent(resultHtml)}`);
    } catch (error) {
        console.error('Error in showValidationResultScreen:', error);
        showErrorScreen('Failed to show validation result screen: ' + error.message);
    }
}

// 검증 진행 화면 표시
function showValidatingScreen() {
    try {
        const validatingHtml = `
            <div style="text-align:center;padding:50px;font-family:Arial;background:#1a1a2e;color:white;min-height:100vh;display:flex;flex-direction:column;justify-content:center;align-items:center;">
                <h2 style="color:#00d4ff;margin-bottom:20px;">Validating API Keys</h2>
                <p style="margin-bottom:30px;">Checking API key validation...</p>
                <div style="border:4px solid #333;border-top:4px solid #00d4ff;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div>
                <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
            </div>
        `;
        mainWindow.loadURL(`data:text/html,${encodeURIComponent(validatingHtml)}`);
    } catch (error) {
        console.error('Error in showValidatingScreen:', error);
        showErrorScreen('Failed to show validating screen: ' + error.message);
    }
}

// 앱 메뉴 생성
function createApplicationMenu() {
    try {
        const template = [
            {
                label: 'File',
                submenu: [
                    {
                        label: 'Settings',
                        accelerator: 'CmdOrCtrl+,',
                        click() {
                            showConfigScreen();
                        }
                    },
                    {
                        type: 'separator'
                    },
                    {
                        label: 'Restart App',
                        accelerator: 'CmdOrCtrl+R',
                        click() {
                            app.relaunch();
                            app.exit();
                        }
                    },
                    {
                        type: 'separator'
                    },
                    {
                        label: 'Exit',
                        accelerator: process.platform === 'darwin' ? 'Cmd+Q' : 'Ctrl+Q',
                        click() {
                            app.quit();
                        }
                    }
                ]
            },
            {
                label: 'View',
                submenu: [
                    {
                        label: 'Reload',
                        accelerator: 'CmdOrCtrl+Shift+R',
                        click() {
                            if (mainWindow) mainWindow.reload();
                        }
                    },
                    {
                        label: 'Toggle Developer Tools',
                        accelerator: process.platform === 'darwin' ? 'Alt+Cmd+I' : 'Ctrl+Shift+I',
                        click() {
                            if (mainWindow) mainWindow.webContents.toggleDevTools();
                        }
                    }
                ]
            },
            {
                label: 'Help',
                submenu: [
                    {
                        label: 'About',
                        click() {
                            showAboutDialog();
                        }
                    }
                ]
            }
        ];

        const menu = Menu.buildFromTemplate(template);
        Menu.setApplicationMenu(menu);
        console.log('Application menu created');
    } catch (error) {
        console.error('Failed to create application menu:', error);
    }
}

// About 다이얼로그 표시
function showAboutDialog() {
    try {
        const aboutHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); max-width: 500px;">
                    <h1 style="margin-bottom: 20px;">Steam Game Recommend</h1>
                    <p style="font-size: 18px; margin-bottom: 15px;">Version 1.0.0</p>
                    <p style="margin-bottom: 20px;">AI-powered Steam game recommendation system</p>
                    <button onclick="window.close()" style="padding: 10px 20px; background: #2CC934; color: white; border: none; border-radius: 5px; cursor: pointer; font-weight: 600;">Close</button>
                </div>
            </div>
        `;

        const aboutWindow = new BrowserWindow({
            width: 600,
            height: 500,
            modal: true,
            parent: mainWindow,
            webPreferences: {
                nodeIntegration: true,
                contextIsolation: false
            }
        });

        aboutWindow.loadURL(`data:text/html,${encodeURIComponent(aboutHtml)}`);
        aboutWindow.setMenuBarVisibility(false);
    } catch (error) {
        console.error('Failed to show about dialog:', error);
    }
}

// IPC 핸들러들
ipcMain.handle('get-api-keys', () => {
    console.log('IPC: get-api-keys called');
    return apiKeys;
});

ipcMain.handle('save-api-keys', (event, keys) => {
    console.log('IPC: save-api-keys called');
    return saveApiKeys(keys);
});

ipcMain.handle('validate-steam-api', async (event, apiKey) => {
    console.log('IPC: validate-steam-api called');
    return await validateSteamApiKey(apiKey);
});

ipcMain.handle('validate-gemini-api', async (event, apiKey) => {
    console.log('IPC: validate-gemini-api called');
    return await validateGeminiApiKey(apiKey);
});

ipcMain.handle('start-main-app', () => {
    console.log('IPC: start-main-app called');
    startMainApp();
});

// 설정 화면 표시 IPC 핸들러
ipcMain.handle('show-config-screen', () => {
    console.log('IPC: show-config-screen called');
    showConfigScreen();
});

// API 키 재검증 IPC 핸들러
ipcMain.handle('revalidate-api-keys', async () => {
    console.log('IPC: revalidate-api-keys called');
    return await validateAllApiKeys();
});

// 메인 앱 시작
function startMainApp() {
    console.log('Starting main application...');
    try {
        // 로딩 화면 표시
        showLoadingScreen();

        // Java 앱 시작
        setTimeout(() => {
            startJavaApp();
        }, 1000);
    } catch (error) {
        console.error('Error in startMainApp:', error);
        showErrorScreen('Failed to start main application: ' + error.message);
    }
}

// 로딩 화면 표시
function showLoadingScreen() {
    try {
        const appRoot = getAppRootPath();
        let loadingPath;

        if (app.isPackaged) {
            // 빌드된 exe에서는 extraResources 경로 사용
            loadingPath = path.join(process.resourcesPath, 'resources', 'html', 'loading.html');
        } else {
            // 개발 환경
            loadingPath = path.join(appRoot, 'resources', 'html', 'loading.html');
        }

        console.log('Looking for loading.html at:', loadingPath);

        if (fs.existsSync(loadingPath)) {
            console.log('Loading loading.html from file');
            mainWindow.loadFile(loadingPath);
        } else {
            console.log('loading.html not found, using inline loading screen');
            const loadingHtml = `
                <div style="text-align:center;padding:50px;font-family:Arial;background:#1a1a2e;color:white;min-height:100vh;display:flex;flex-direction:column;justify-content:center;align-items:center;">
                    <h2 style="color:#00d4ff;margin-bottom:20px;">Steam Game Recommend</h2>
                    <p style="margin-bottom:30px;">서버를 시작하고 있습니다...</p>
                    <div style="border:4px solid #333;border-top:4px solid #00d4ff;border-radius:50%;width:40px;height:40px;animation:spin 2s linear infinite;margin:20px auto;"></div>
                    <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
                </div>
            `;
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(loadingHtml)}`);
        }
    } catch (error) {
        console.error('Error in showLoadingScreen:', error);
        showErrorScreen('Failed to show loading screen: ' + error.message);
    }
}

// 설정 화면 표시
function showConfigScreen() {
    try {
        const appRoot = getAppRootPath();
        let configPath;

        if (app.isPackaged) {
            // 빌드된 exe에서는 extraResources 경로 사용
            configPath = path.join(process.resourcesPath, 'resources', 'html', 'config.html');
        } else {
            // 개발 환경
            configPath = path.join(appRoot, 'resources', 'html', 'config.html');
        }

        console.log('Looking for config.html at:', configPath);

        if (fs.existsSync(configPath)) {
            console.log('Loading config.html from file');
            mainWindow.loadFile(configPath);
        } else {
            console.log('config.html not found, using inline config screen');
            const configHtml = `
                <div style="padding: 40px; font-family: Arial; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                    <div style="background: rgba(255,255,255,0.1); padding: 40px; border-radius: 15px; backdrop-filter: blur(10px); width: 600px; max-width: 90%;">
                        <h2 style="text-align: center; margin-bottom: 30px;">API Key Configuration</h2>
                        
                        <div style="margin-bottom: 25px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600;">Steam API Key:</label>
                            <input type="text" id="steamApiKey" placeholder="Enter your Steam API key" style="width: 100%; padding: 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                                Get your Steam API key from: steamcommunity.com/dev/apikey
                            </div>
                        </div>

                        <div style="margin-bottom: 25px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600;">Gemini API Key:</label>
                            <input type="text" id="geminiApiKey" placeholder="Enter your Gemini API key" style="width: 100%; padding: 12px; border: none; border-radius: 8px; background: rgba(255,255,255,0.9); color: #333; font-size: 14px; box-sizing: border-box;">
                            <div style="font-size: 12px; color: rgba(255,255,255,0.8); margin-top: 5px;">
                                Get your Gemini API key from: aistudio.google.com/app/apikey
                            </div>
                        </div>

                        <div style="display: flex; gap: 10px; margin-top: 30px;">
                            <button onclick="skipConfig()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);">Skip for Now</button>
                            <button onclick="saveConfig()" style="flex: 1; padding: 12px 20px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; background: #2CC934; color: white;">Save & Continue</button>
                        </div>
                        <div id="status" style="margin-top: 15px; padding: 10px; border-radius: 5px; text-align: center; font-weight: 600; display: none;"></div>
                    </div>
                </div>
                <script>
                    const { ipcRenderer } = require('electron');
                    
                    // 기존 API 키 로드
                    window.addEventListener('DOMContentLoaded', () => {
                        ipcRenderer.invoke('get-api-keys').then(keys => {
                            if (keys.steamApiKey) document.getElementById('steamApiKey').value = keys.steamApiKey;
                            if (keys.geminiApiKey) document.getElementById('geminiApiKey').value = keys.geminiApiKey;
                        }).catch(err => console.error('Failed to load API keys:', err));
                    });
                    
                    async function saveConfig() {
                        const steamApiKey = document.getElementById('steamApiKey').value.trim();
                        const geminiApiKey = document.getElementById('geminiApiKey').value.trim();
                        const statusDiv = document.getElementById('status');
                        
                        try {
                            await ipcRenderer.invoke('save-api-keys', { steamApiKey, geminiApiKey });
                            statusDiv.style.display = 'block';
                            statusDiv.style.background = 'rgba(76, 175, 80, 0.2)';
                            statusDiv.style.border = '1px solid #2CC934';
                            statusDiv.textContent = 'API keys saved successfully!';
                            setTimeout(() => { ipcRenderer.invoke('start-main-app'); }, 1500);
                        } catch (error) {
                            statusDiv.style.display = 'block';
                            statusDiv.style.background = 'rgba(244, 67, 54, 0.2)';
                            statusDiv.style.border = '1px solid #f44336';
                            statusDiv.textContent = 'Failed to save API keys: ' + error.message;
                        }
                    }
                    
                    function skipConfig() {
                        ipcRenderer.invoke('start-main-app');
                    }
                </script>
            `;
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(configHtml)}`);
        }
    } catch (error) {
        console.error('Error in showConfigScreen:', error);
        showErrorScreen('Failed to show config screen: ' + error.message);
    }
}

// 데이터베이스 초기화 파일들 존재 확인 및 로깅
function checkDatabaseFiles(workingDirectory) {
    console.log('=== Checking Database Files ===');

    const dataFolder = path.join(workingDirectory, 'data');
    const initSqlPath = path.join(dataFolder, 'init.sql');
    const dataSqlPath = path.join(dataFolder, 'data.sql');

    console.log('Data folder path:', dataFolder);
    console.log('Init SQL path:', initSqlPath);
    console.log('Data SQL path:', dataSqlPath);

    const initExists = fs.existsSync(initSqlPath);
    const dataExists = fs.existsSync(dataSqlPath);

    console.log(`init.sql exists: ${initExists ? 'YES' : 'NO'}`);
    console.log(`data.sql exists: ${dataExists ? 'YES' : 'NO'}`);

    if (initExists) {
        console.log('✓ Found init.sql - 테이블 스키마가 생성됩니다');
    } else {
        console.log('⚠ init.sql not found - 테이블 스키마가 생성되지 않을 수 있습니다');
    }

    if (dataExists) {
        console.log('✓ Found data.sql - 초기 데이터가 삽입됩니다');
    } else {
        console.log('⚠ data.sql not found - 초기 데이터가 삽입되지 않을 수 있습니다');
    }

    return { initExists, dataExists };
}

// Java 앱 시작 - 수정된 경로 처리 및 데이터베이스 파일 확인
// Java 앱 시작 함수 수정
function startJavaApp() {
    console.log('=== Starting Java App ===');

    try {
        const appRoot = getAppRootPath();

        // JAR 파일 경로 찾기 (기존 코드와 동일)
        const possiblePaths = [
            path.join(process.resourcesPath, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(process.resourcesPath, 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(appRoot, 'resources', 'recommend-0.0.1-SNAPSHOT.jar'),
            path.join(appRoot, '..', 'build', 'libs', 'recommend-0.0.1-SNAPSHOT.jar')
        ];

        let jarPath = null;
        console.log('Searching for JAR file...');
        for (const testPath of possiblePaths) {
            console.log(`  Checking: ${testPath}`);
            if (fs.existsSync(testPath)) {
                jarPath = testPath;
                console.log(`  Found JAR at: ${jarPath}`);
                break;
            }
        }

        if (!jarPath) {
            console.error('JAR file not found');
            showErrorScreen('JAR file not found: recommend-0.0.1-SNAPSHOT.jar');
            return;
        }

        // 수정: 작업 디렉토리를 JAR 파일이 있는 디렉토리로 설정
        const workingDirectory = path.dirname(jarPath);
        console.log('Working directory:', workingDirectory);

        // 데이터베이스 관련 경로 확인
        const possibleDataPaths = [
            path.join(workingDirectory, 'data'),
            path.join(workingDirectory, '..', 'data'),
            path.join(process.resourcesPath, 'resources', 'data'),
            path.join(appRoot, 'resources', 'data')
        ];

        let dataPath = null;
        for (const testPath of possibleDataPaths) {
            console.log(`  Checking data folder: ${testPath}`);
            if (fs.existsSync(testPath)) {
                dataPath = testPath;
                console.log(`  Found data folder at: ${dataPath}`);
                break;
            }
        }

        // 데이터베이스 파일 존재 여부 확인
        let shouldInitializeData = false;
        if (dataPath) {
            const dbPath = path.join(dataPath, 'gamedb.mv.db');
            const dbExists = fs.existsSync(dbPath);

            console.log(`Database file exists: ${dbExists ? 'YES' : 'NO'}`);

            if (dbExists) {
                console.log('✓ 기존 데이터베이스 파일 발견 - 기존 데이터 사용');
                shouldInitializeData = false;
            } else {
                console.log('ℹ 데이터베이스 파일 없음 - 초기 데이터 생성 필요');
                shouldInitializeData = true;

                // data 폴더가 없으면 생성
                if (!fs.existsSync(dataPath)) {
                    fs.mkdirSync(dataPath, { recursive: true });
                    console.log(`Created data directory: ${dataPath}`);
                }
            }
        } else {
            // data 폴더 생성
            dataPath = path.join(workingDirectory, 'data');
            fs.mkdirSync(dataPath, { recursive: true });
            console.log(`Created data directory: ${dataPath}`);
            shouldInitializeData = true;
        }

        // 환경변수 설정
        const env = {
            ...process.env,
            STEAM_API_KEY: apiKeys.steamApiKey || '',
            GEMINI_API_KEY: apiKeys.geminiApiKey || '',
            SPRING_PROFILES_ACTIVE: 'h2',
            // 데이터베이스 경로 명시적 설정
            SPRING_DATASOURCE_URL: `jdbc:h2:file:${dataPath ? path.join(dataPath, 'gamedb') : './data/gamedb'}`,
            // 데이터 초기화 여부 설정
            INIT_DB: shouldInitializeData ? 'always' : 'never'
        };

        console.log('Environment variables:', {
            STEAM_API_KEY: env.STEAM_API_KEY ? 'SET' : 'NOT SET',
            GEMINI_API_KEY: env.GEMINI_API_KEY ? 'SET' : 'NOT SET',
            SPRING_PROFILES_ACTIVE: env.SPRING_PROFILES_ACTIVE,
            SPRING_DATASOURCE_URL: env.SPRING_DATASOURCE_URL,
            INIT_DB: env.INIT_DB
        });

        // 기존 프로세스 종료
        if (javaProcess) {
            console.log('Terminating existing Java process...');
            javaProcess.kill();
        }

        // Java 실행 인자 수정
        const javaArgs = [
            '-jar', jarPath,
            '--spring.profiles.active=h2',
            `--spring.sql.init.mode=${shouldInitializeData ? 'always' : 'never'}`,
            '--spring.jpa.defer-datasource-initialization=true',
            '--logging.level.org.springframework.jdbc=DEBUG',
            '--logging.level.org.springframework.boot.autoconfigure.sql=DEBUG',
            '--logging.level.org.hibernate.SQL=DEBUG'
        ];

        console.log('Java arguments:', javaArgs);

        javaProcess = spawn('java', javaArgs, {
            stdio: ['ignore', 'pipe', 'pipe'],
            cwd: workingDirectory,
            env: env
        });

        // 출력 모니터링 강화
        javaProcess.stdout.on('data', (data) => {
            const output = data.toString();
            console.log('Java stdout:', output);

            // 데이터베이스 초기화 관련 로그 모니터링
            if (output.includes('HikariPool')) {
                console.log('✓ 데이터베이스 연결 풀 초기화됨');
            }
            if (output.includes('H2 database') || output.includes('Starting embedded database')) {
                console.log('✓ H2 데이터베이스 연결됨');
            }
            if (output.includes('Executing SQL script') || output.includes('ScriptUtils')) {
                console.log('✓ SQL 스크립트 실행 중:', output.trim());
            }
            if (output.includes('schema.sql')) {
                console.log('✓ 스키마 파일 실행됨 - 테이블 생성');
            }
            if (output.includes('data.sql')) {
                console.log('✓ 데이터 파일 실행됨 - 초기 데이터 삽입');
            }
            if (output.includes('Hibernate: create table') || output.includes('Hibernate: drop table')) {
                console.log('✓ Hibernate DDL 실행:', output.trim());
            }
            if (output.includes('Hibernate: insert into') || output.includes('Hibernate: select')) {
                console.log('✓ 데이터베이스 쿼리 실행:', output.trim());
            }
            if (output.includes('DataInitializer')) {
                console.log('✓ 데이터 초기화 컴포넌트 실행:', output.trim());
            }
            if (output.includes('이미') && output.includes('게임이 있습니다')) {
                console.log('✓ 기존 데이터 발견 - 초기화 건너뜀');
            }
            if (output.includes('게임 데이터를 삽입합니다')) {
                console.log('✓ 약 8000개 게임 데이터 삽입 시작... (시간이 걸릴 수 있습니다)');
            }
            if (output.includes('데이터 삽입 진행중')) {
                console.log('✓ 데이터 삽입 진행:', output.trim());
            }
            if (output.includes('데이터 삽입 완료')) {
                console.log('✓ 모든 게임 데이터 삽입 완료!');
            }
            if (output.includes('외래키 체크')) {
                console.log('✓ 외래키 설정:', output.trim());
            }

            // 서버 시작 확인
            if (output.includes('Started RecommendApplication') ||
                output.includes('Tomcat started on port 8888')) {
                console.log('✓ Java 서버 시작 완료!');
                isJavaServerReady = true;
                setTimeout(() => checkServerAndConnect(), 2000);
            }
        });

        javaProcess.stderr.on('data', (data) => {
            const errorOutput = data.toString();
            console.error('Java stderr:', errorOutput);

            // 데이터베이스 관련 에러 특별 처리
            if (errorOutput.includes('SQLException') ||
                errorOutput.includes('DataAccessException') ||
                errorOutput.includes('JdbcTemplate')) {
                console.error('!!! 데이터베이스 관련 오류 발생 !!!');
            }
        });

        javaProcess.on('close', (code) => {
            console.log(`Java process terminated with code: ${code}`);
            isJavaServerReady = false;
            if (code !== 0 && code !== null) {
                showErrorScreen(`Java application exited with error code: ${code}`);
            }
        });

        javaProcess.on('error', (error) => {
            console.error('Failed to start Java process:', error);
            isJavaServerReady = false;
            if (error.code === 'ENOENT') {
                showErrorScreen('Java is not installed or not found in PATH.');
            } else {
                showErrorScreen('Failed to start Java process: ' + error.message);
            }
        });

    } catch (error) {
        console.error('Error in startJavaApp:', error);
        showErrorScreen('Error starting Java process: ' + error.message);
    }
}

// 서버 연결 확인 및 index.html 로드
async function checkServerAndConnect() {
    console.log('=== Checking Server Connection ===');

    const maxAttempts = 15;
    let attempts = 0;

    while (attempts < maxAttempts) {
        try {
            console.log(`Connection attempt ${attempts + 1}/${maxAttempts}...`);

            const response = await fetch('http://localhost:8888/index.html', {
                method: 'GET',
                signal: AbortSignal.timeout(3000)
            });

            if (response.ok) {
                console.log('Server connection successful!');
                mainWindow.loadURL('http://localhost:8888/index.html');
                return;
            }
        } catch (error) {
            console.log(`Server not ready yet: ${error.message}`);
        }

        await new Promise(resolve => setTimeout(resolve, 1000));
        attempts++;
    }

    console.log('Server connection failed');
    showErrorScreen('Failed to connect to server after maximum attempts.');
}

// 에러 화면 표시
function showErrorScreen(message) {
    try {
        const errorHtml = `
            <div style="padding: 40px; font-family: Arial; text-align: center; background: #f44336; color: white; min-height: 100vh; display: flex; align-items: center; justify-content: center;">
                <div style="max-width: 600px;">
                    <h1>Error</h1>
                    <p style="font-size: 18px; margin: 20px 0; line-height: 1.6;">${message}</p>
                    <div style="margin-top: 30px;">
                        <button onclick="location.reload()" style="padding: 12px 24px; font-size: 16px; background: white; color: #f44336; border: none; border-radius: 8px; cursor: pointer; font-weight: 600; margin-right: 10px;">Retry</button>
                        <button onclick="window.close()" style="padding: 12px 24px; font-size: 16px; background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3); border-radius: 8px; cursor: pointer; font-weight: 600;">Close</button>
                    </div>
                </div>
            </div>
        `;
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.loadURL(`data:text/html,${encodeURIComponent(errorHtml)}`);
        }
    } catch (error) {
        console.error('Error in showErrorScreen:', error);
    }
}

// 수정된 createWindow 함수 - API 키 검증 로직 추가
function createWindow() {
    console.log('=== Creating Window ===');

    try {
        mainWindow = new BrowserWindow({
            width: 1200,
            height: 800,
            show: false,
            webPreferences: {
                nodeIntegration: true,
                contextIsolation: false
            }
        });

        // 애플리케이션 메뉴 생성
        createApplicationMenu();

        mainWindow.once('ready-to-show', () => {
            console.log('Window ready - showing now');
            mainWindow.show();
            mainWindow.focus();

            // 개발 중에는 DevTools 열기
            if (!app.isPackaged) {
                mainWindow.webContents.openDevTools();
            }
        });

        // API 키 로드 및 검증 프로세스
        initializeApiKeys();

    } catch (error) {
        console.error('Error creating window:', error);
        app.quit();
    }
}

// API 키 초기화 및 검증 프로세스
async function initializeApiKeys() {
    console.log('Initializing API keys...');

    try {
        // API 키 로드 시도
        const hasApiKeys = loadApiKeys();

        if (!hasApiKeys || !apiKeys.steamApiKey || !apiKeys.geminiApiKey) {
            console.log('API keys missing or incomplete - showing config screen');
            showConfigScreen();
            return;
        }

        console.log('API keys found - starting validation...');

        // 검증 진행 화면 표시
        showValidatingScreen();

        // API 키 검증 실행
        const validationResults = await validateAllApiKeys();

        // 검증 결과에 따른 처리
        if (validationResults.allValid) {
            console.log('All API keys are valid - starting main app');
            // 잠시 결과 화면을 보여준 후 메인 앱 시작
            showValidationResultScreen(validationResults);
        } else {
            console.log('Some API keys are invalid - showing validation results');
            showValidationResultScreen(validationResults);
        }

    } catch (error) {
        console.error('Error during API key initialization:', error);
        showErrorScreen('Failed to initialize API keys: ' + error.message);
    }
}

// 앱 이벤트 핸들러
app.whenReady().then(() => {
    console.log('=== App Ready ===');
    createWindow();
}).catch(error => {
    console.error('App failed to become ready:', error);
});

app.on('window-all-closed', () => {
    console.log('=== All Windows Closed ===');
    // Java 프로세스 종료
    if (javaProcess) {
        console.log('Terminating Java process...');
        javaProcess.kill();
    }

    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    console.log('=== App Activated ===');
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
    }
});

// 앱 종료 시 Java 프로세스 정리
app.on('before-quit', () => {
    console.log('=== App Before Quit ===');
    if (javaProcess) {
        console.log('Cleaning up Java process before quit...');
        javaProcess.kill();
    }
});

console.log('=== Enhanced Main.js with API Key Validation and Database Initialization loaded successfully ===');