🌐 [한국어](README.ko.md) | **English**

# 🎮 Steam Game Recommend API & UI

Steam game recommendation system that uses tag information, user profiles, recent play history

> **Note**: To optimize database size with H2, we only include games that have 50+ reviews. Some games you're looking for might not be available due to this limitation.

---

## 📁 Project Structure

```
recommend/
├── src
│   ├── main
│   │   ├── java/SteamGame.recommend/  # Spring Boot REST API
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/               # HTML-based UI
│   │           ├── index.html        # Home (Feature menu)
│   │           ├── random.html       # Tag-based random recommendations
│   │           ├── input.html        # Direct input tag recommendations
│   │           ├── profile.html      # Profile-based recommendations
│   │           ├── recent.html       # Recent 2-week play recommendations
│   │           ├── similar.html      # Similar tag recommendations
│   │           ├── choice.html       # Game choice interactive feature
│   │           └── review-guess.html # Review guessing game
├── electron/                         # Electron desktop app
│   ├── main.js                       # Main process
│   ├── package.json                  # Build configuration
│   ├── html/
│   │   ├── config.html               # API key settings
│   │   └── loading.html              # Loading screen
│   └── resources/
│       └── recommend-0.0.1-SNAPSHOT.jar
└── data/                             # H2 Database files (auto-created)
    └── gamedb.mv.db                  # H2 database file
```

---

## 📑 Prerequisites

- **General Requirements**
    - JDK 17 or higher, Git
    - No external database required (H2 embedded)
- **Desktop Application**
    - Windows 10/11 (64-bit)
    - Java 17 or higher installed
    - API keys can be configured when running the app

---

## 🔑 API Key Setup

### 1. Steam Web API Key

1. Visit [Steam API Key Registration Page](https://steamcommunity.com/dev/apikey) and log in with your Steam account
2. Enter any domain name (e.g., `localhost`)
3. Check "I agree" and click "Register"
4. Copy and save the generated API Key

> Note: Steam Guard verification required. Community-restricted accounts cannot obtain API keys.
>

### 2. Google Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com/app/apikey) and log in
2. Click "Create API Key"
3. Copy and save the generated API Key

---

## 🖥️ How to Run

### Method 1: Desktop Application (Recommended)

1. **Download & Install**
    - Download the latest version from the [Releases page](https://github.com/tember8003/SteamGame_Recommend/releases)
    - Run `Steam-Game-Recommend-Setup-x.x.x.exe`

2. **Launch Application**
    - API key configuration screen appears automatically on first run
    - Enter your Steam API Key and Gemini API Key
    - Click `Skip for Now` to use tag search without API keys
    - API keys are stored locally at: `%AppData%\steam-game-recommend\api-keys.json`

3. **Use Features**
    - All features run in a single application
    - No separate server environment variable setup required

---

### Method 2: Developer/Server Execution

1. **Clone Project**

    ```bash
    git clone https://github.com/tember8003/SteamGame_Recommend.git
    cd SteamGame_Recommend
    ```

2. **Set Environment Variables** (Optional)
    - macOS/Linux (bash, zsh)

       ```bash
       export STEAM_API_KEY=YOUR_STEAM_API_KEY
       export GEMINI_API_KEY=YOUR_GEMINI_API_KEY
       ```

    - Windows (PowerShell)

       ```powershell
       $Env:STEAM_API_KEY = "YOUR_STEAM_API_KEY"
       $Env:GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
       ```

3. **Start Server**

    ```bash
    cd backend
    ./gradlew clean bootRun
    ```

    - Default server: [http://localhost:8888](http://localhost:8888/)
    - H2 Console: http://localhost:8888/h2-console (for database management)
    - Swagger-UI: http://localhost:8888/docs
    - OpenAPI JSON: http://localhost:8888/v3/api-docs

---

## 🖥️ UI Pages

| Feature Description | Path |
| --- | --- |
| Home (Feature selection) | `/` |
| Tag-based recommendations | `/random.html` |
| Direct input tag recommendations | `/input.html` |
| Profile-based recommendations | `/profile.html` |
| Recent 2-week play recommendations | `/recent.html` |
| Similar tag recommendations | `/similar.html` |

---

## 📦 Electron App Build (For Developers)

1. **Setup**

    ```bash
    cd backend/electron
    npm install
    ```

2. **Prepare JAR File**

    ```bash
    # Build JAR from main project
    cd ..
    ./gradlew clean bootJar
    
    # Copy JAR file
    cp build/libs/recommend-0.0.1-SNAPSHOT.jar electron/resources/
    ```

3. **Build Application**

    ```bash
    cd electron
    npm run build-win  # Windows build
    ```

    - Build output: `electron/dist/`

---

## ⚙️ Configuration Example

```properties
# API Keys
steam.api.key=${STEAM_API_KEY}
spring.ai.google.api-key=${GEMINI_API_KEY}

# H2 Database Configuration
spring.datasource.url=jdbc:h2:file:./data/gamedb;DB_CLOSE_DELAY=-1;MODE=MySQL;AUTO_SERVER=TRUE;DATABASE_TO_LOWER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;CHARSET=UTF-8
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Server Configuration
server.port=8888
```

---

## 📖 Key Features

- **🔍 Tag-based Recommendations**
    - Random recommendations: Random game suggestions based on tags
    - Similar tags: Game recommendations with tags similar to specific games
- **😊 Profile-based Recommendations**
    - Steam ID library analysis
- **⏰ Recent Play-based Recommendations**
    - Analysis of games played in the last 2 weeks
    - Recommendations based on current interests

---

## 🆘 Troubleshooting

### Desktop Application

- **Java Error**: Verify Java 17 or higher is installed
- **API Key Error**: Verify correct API keys are entered
- **Port Conflict**: Check if port 8888 is in use

### Server Execution

- **Port Conflict**: Verify port 8888 is not in use by other applications
- **Environment Variable Error**: Verify API keys are set correctly (optional for basic functionality)
- **Database Error**: H2 database files are automatically created in `./data/` directory

---

## 📁 API Key Storage Location

The desktop application stores API keys locally at:
- **Windows**: `%AppData%\steam-game-recommend\api-keys.json`
- **Full path example**: `C:\Users\[Username]\AppData\Roaming\steam-game-recommend\api-keys.json`

This ensures your API keys remain private and are not shared with others.

---

## 💾 Database

The application uses **H2 embedded database** with the following features:
- **Automatic initialization**: Database and tables are created automatically on first run
- **Data persistence**: Game data persists between application restarts stored in `./data/gamedb` files
- **Special character support**: Proper handling of Unicode characters including ®, ™ symbols
- **MySQL compatibility mode**: Ensures compatibility with MySQL syntax
- **H2 Console**: Built-in web interface for database management at `/h2-console`
- **No external dependencies**: No need for separate database server installation

---

## 🌐 API Endpoints

### Game Recommendations
- `POST /api/recommend/random` - Tag-based random recommendations
- `POST /api/recommend/input` - Text input-based recommendations
- `POST /api/recommend/profile` - Profile-based recommendations
- `POST /api/recommend/recent-play` - Recent play-based recommendations
- `POST /api/recommend/similar` - Similar game recommendations

### Game Data
- `GET /api/games/appid?appid={id}` - Get game by AppID
- `GET /api/games/tags` - Get all available tags
- `GET /api/games/search?query={name}` - Search games by name

### Interactive Features
- `POST /api/choice/start` - Start game choice session
- `POST /api/choice/select` - Submit choice selection
- `POST /api/choice/result` - Get choice recommendations

### Review Guess Game
- `POST /api/review-guess/start` - Start review guess game
- `POST /api/review-guess/answer` - Submit answer
- `GET /api/review-guess/{sessionId}/result` - Get game results

---

## 🛠️ Technology Stack

- **Backend**: Spring Boot 3.x, Java 17
- **Database**: H2 (embedded, production) / MySQL (development)
- **Frontend**: HTML, CSS, JavaScript
- **Desktop**: Electron
- **AI Integration**: Google Gemini API
- **External API**: Steam Web API
- **Build**: Gradle
- **Database Console**: H2 Console (built-in web interface)

---

## 📄 License

This project is available under the MIT License.

---

## 📞 Support

For project-related issues, please register them on the [Issues page](https://github.com/tember8003/SteamGame_Recommend/issues).