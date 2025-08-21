#### 1. Project Vision & Executive Summary
The OptiHealth is a comprehensive platform designed to empower individuals by enabling them to collect, store, visualize, and analyze a wide array of personal health and wellness data. The core mission is to transform siloed, raw data from wearables, apps, and manual logs into a unified, secure database, and then leverage modern data analysis and AI to uncover personalized, actionable insights.
The platform will consist of a Supabase backend, a feature-rich Next.js web application, and a dedicated Android application for automated data synchronization. Users will be able to track everything from sleep patterns and heart rate variability to supplement intake and application usage, asking natural language questions to an AI assistant to understand correlations and trends within their own data.
#### 2. System Architecture
The OptiHealth ecosystem is built on a modern, scalable architecture centered around Supabase as the Backend-as-a-Service (BaaS).
```graph TD
    subgraph User Devices
        WebApp[Web Application (Next.js)]
        AndroidApp[Android Application (Kotlin)]
    end

    subgraph Cloud Backend
        subgraph Supabase
            Auth[Supabase Auth]
            DB[Supabase Database (PostgreSQL)]
            EdgeFunc[Supabase Edge Functions]
        end
        PythonService[Python Analysis Service]
    end

    subgraph External Services
        HealthConnect[Health Connect by Android]
        AppUsageAPI[Android UsageStatsManager API]
        LLM_Service[LLM Service (e.g., OpenAI, Anthropic)]
    end

    WebApp -- HTTPS, Google Login --> Auth
    WebApp -- Data Viz & Manual Logging --> EdgeFunc
    WebApp -- User Queries --> LLM_Service
    WebApp -- Reads pre-computed insights --> DB

    AndroidApp -- HTTPS --> Auth
    AndroidApp -- Reads health data --> HealthConnect
    AndroidApp -- Reads app usage --> AppUsageAPI
    AndroidApp -- Compressed Data Sync --> EdgeFunc

    EdgeFunc -- SQL (via RPC) --> DB
    EdgeFunc -- Secure API Call --> LLM_Service
    EdgeFunc -- Secure Webhook --> PythonService

    PythonService -- Reads raw data --> DB
    PythonService -- Writes analysis results --> DB

```
- Web Application: The primary user interface for data visualization, manual logging, and interaction with the AI insights assistant.
- Android Application (Kotlin): A background service-oriented app that automatically syncs health and usage data from the device to Supabase.
- Supabase Backend: Provides all necessary backend infrastructure, including user authentication, a powerful PostgreSQL database, and serverless Edge Functions for business logic and data ingestion.
- Python Analysis Service: An external, asynchronous service responsible for computationally intensive tasks, including machine learning model training and deep insight generation.
- External Services: Integrates with Android's Health Connect and UsageStatsManager APIs for data collection and a chosen LLM provider for natural language processing.
#### 3. Advanced Storage Architecture & Data Schema
The data storage layer of OptiHealth is engineered for high performance, massive scalability, and deep analytical power. We have moved beyond a simple relational model to a hybrid architecture that leverages specialized PostgreSQL extensions for time-series data, vector search, and efficient querying. This ensures a fast user experience and unlocks sophisticated, AI-driven insights.
#### 3.1. Core Architectural Principles
- Normalized & Tidy: We use a "hub-and-spoke" model centered around a canonical metric_definitions table. This enforces data consistency and integrity, ensuring every data point is unambiguous.
- Pre-Aggregated for Speed: To power a responsive dashboard, we use multi-level summary tables ( daily_summaries, time_aggregated_summaries). The UI queries these small, pre-calculated tables instead of aggregating millions of raw data points on the fly.
- AI-Ready with Vector Search: By integrating the pgvector extension, we store text data (like journal entries) as vector embeddings. This enables true semantic search, allowing users to find data based on meaning and context, not just keywords.
#### 3.2. Key Technical Enhancements
#### 3.2.2. pgvector for Semantic Analysis
To enable powerful, context-aware analysis of unstructured text, we use vector embeddings.
- How it Works: A journal entry like "Felt tired and unmotivated" is converted by an AI model into a numerical vector. The pgvector extension allows us to store this vector in our events table and perform efficient similarity searches.
- Benefit: This unlocks queries like, "Show me my sleep quality on days I felt anxious" or "Correlate my heart rate with times I felt highly productive." The system can find semantically related journal entries and join them with quantitative health data.
#### 3.2.3. GIN Indexing for JSONB
The value_json and tags columns offer flexibility. To make them performant, we use GIN (Generalized Inverted Indexes).
- Benefit: A GIN index allows PostgreSQL to efficiently search for keys and values inside a JSONB object. Without it, filtering by a tag or a JSON property would be prohibitively slow, requiring a full scan of the table.
#### 3.2.4. Storing Time-Series Sequences: The Sleep Stages Model
Sleep data is fundamentally different from a single point-in-time metric like resting heart rate. It is a sequence of states over a continuous period. To capture this rich, ordered data without creating a separate, complex table, we leverage the value_json column in our main data_points table.
- How it Works: An entire night's sleep stages are stored as a single entry in the data_points table with the corresponding metric_id for sleep_stages. The timestamp for this entry represents the start of the sleep session. The detailed sequence of stages is encoded as a JSON array within the value_json field.
- Data Structure: Each object within the JSON array represents a distinct sleep stage segment and adheres to a strict schema:
  JSON
  ```[
{
  "stage": "awake",
  "startTimestamp": "2024-10-23T22:05:00Z",
  "endTimestamp": "2024-10-23T22:15:00Z",
  "durationSeconds": 600
},
{
  "stage": "light",
  "startTimestamp": "2024-10-23T22:15:00Z",
  "endTimestamp": "2024-10-23T23:05:00Z",
  "durationSeconds": 3000
},
{
  "stage": "deep",
  "startTimestamp": "2024-10-23T23:05:00Z",
  "endTimestamp": "2024-10-23T23:45:00Z",
  "durationSeconds": 2400
},
{
  "stage": "rem",
  "startTimestamp": "2024-10-23T23:45:00Z",
  "endTimestamp": "2024-10-24T00:10:00Z",
  "durationSeconds": 1500
}
]

```
- Benefits:
	- Atomicity: Keeps all data for a single sleep session encapsulated in one database row, simplifying queries and ensuring data integrity.
	- Performance: Leverages PostgreSQL's powerful JSONB functions and the existing GIN index on value_json for efficient querying (e.g., calculating total deep sleep time or counting awakenings directly in the database).
	- Simplicity: Avoids the need for an additional sleep_stage_segments table, keeping the core schema lean and tidy while maintaining high data fidelity.
#### 4. Metric Definitions
This is the canonical registry of all trackable metrics, stored in the metric_definitions table. This centralized approach ensures data consistency across the entire platform.
#### Trainings 🏃‍♂️
- activity_steps: Total steps taken in a day. count.
- workout_duration: Total duration of a specific workout session. seconds.
- workout_distance: Total distance covered during a workout. meters.
- workout_calories_burned: Estimated calories burned during a workout. kcal.
- workout_type: The type of workout performed (e.g., "Running", "Cycling"). text.
#### Vitals & Heart ❤️
- heartrate_resting: Resting heart rate, typically measured in the morning. bpm.
- heartrate_avg: Average heart rate over a specific period. bpm.
- hrv_rmssd: The root mean square of successive differences, a key HRV metric. milliseconds.
- body_temperature: Core or surface body temperature. celsius.
- blood_oxygen_spo2: Blood oxygen saturation level, a measure of oxygen in the blood. percentage.
- respiratory_rate: The number of breaths taken per minute. breaths_per_minute.
#### Sleep 😴
- sleep_duration_total: Total time spent asleep. minutes.
- sleep_duration_deep: Total time spent in the deep sleep stage. minutes.
- sleep_duration_light: Total time spent in the light sleep stage. minutes.
- sleep_duration_rem: Total time spent in the REM sleep stage. minutes.
- sleep_duration_awake: Total time spent awake during the sleep session. minutes.
- sleep_score: A composite score from 0-100 indicating overall sleep quality. number.
- sleep_stages: A JSON array detailing the sequence and duration of sleep stages. json.
#### Mental Health & Mood 😊
- mental_health_log: A free-text entry for daily journaling or mood logging. text.
#### Environment 🌳
- environment_uv_index: The ultraviolet (UV) index level. index.
- environment_noise_level: Average ambient sound/noise level. dB.
- environment_air_quality: Air Quality Index (AQI). aqi.
- environment_pressure: Atmospheric pressure. hPa.
- environment_location: GPS coordinates or location data. geography.
#### Nutrition 🥗
- nutrition_calories: Total calories consumed. kcal.
- nutrition_sugar: Total sugar consumed. grams.
- nutrition_glucose_blood: Blood glucose level. mg/dL.
#### 5. Complete SQL Schema & Implementation
The following SQL script creates the entire data storage infrastructure. It is designed to be run in a Supabase project.
#### 5.1. SQL Schema Analysis & Correctness
The schema is structured for correctness and performance:
- Foreign Keys: All relationships (e.g., data_points.metric_id to metric_definitions.id) are enforced with foreign key constraints, guaranteeing data integrity.
- Indexes: Standard B-tree indexes are placed on foreign keys and common query columns ( user_id, metric_id, timestamp). Specialized GIN indexes are used for JSONB columns.
- Security: Row-Level Security (RLS) is enabled on all tables, ensuring users can only access their own data.
- Hypertables: The create_hypertable command correctly converts the main data table for time-series optimization.
- Vector Support: The events table is correctly altered to include a vector column for semantic search capabilities.
#### 5.3. Future Consideration: Schema Registry
While not implemented in this version, a future improvement would be to implement a Schema Registry within our data ingestion Edge Function. This would involve using a library like ajv to validate the structure of incoming value_json data against a predefined schema for each metric. This acts as a "data contract," preventing malformed or inconsistent JSON from ever entering the database, which is crucial for long-term data quality and analytical reliability.
#### 4. Key Mechanisms & Implementation Guides
#### 4.1. High-Performance Data Ingestion from Android
To ensure fast and reliable data uploads, the Android app will use the following pipeline:
1. Chunking: Collect health data points into batches (e.g., 100-500 records) before uploading.
2. Compression: Compress the JSON-serialized batch using Zstandard (zstd) to reduce payload size.
3. Edge Function Invocation: Send the compressed binary data as a POST request to a dedicated Supabase Edge Function ( ingest-data).
4. Decompression & Database Push: The Edge Function authenticates the user, decompresses the zstd payload, and calls the bulk_insert_data_points PostgreSQL function via RPC, passing the entire batch of data. This function handles the efficient, transactional insertion into the database.
5. Duplicate Prevention: The UNIQUE constraint on the data_points table, combined with the ON CONFLICT DO NOTHING clause in the PostgreSQL function, provides robust, database-level prevention of duplicate records. The client app will also track the last successful sync timestamp to avoid re-fetching old data.
#### 4. User Experience & Application Flow
The OptiHealth web application is designed around a clean, intuitive, and goal-oriented user interface. The experience is segmented into three primary pages:
#### 4.1. The Dashboard Page: Your Day at a Glance
- Purpose: To provide an immediate, contextual overview of the current day and make data logging frictionless.
- Composition: A central, vertical timeline displays the day's events chronologically. Key metric widgets at the top show vital stats, and a prominent conversational input allows for natural language data entry.
- Key Interactions: A primary Log button for AI-powered data entry, an + Add Entry button for structured logging, and clickable timeline items for viewing or editing details.
The web app will allow conversational data logging through a chat interface, powered by the Vercel AI SDK.
1. Natural Language Parsing: The user types a command (e.g., "Log that I took 500mg of Vitamin C").
2. LLM Function Calling: The Vercel AI SDK sends this text to an LLM, along with definitions for server-side functions like logHealthDataPoint and logHealthEvent. The LLM intelligently parses the user's text into a structured JSON object matching the arguments for one of these functions.
3. Server-Side Execution: A Next.js API route receives the LLM's structured output and securely executes the corresponding function ( logHealthDataPoint or logHealthEvent).
4. Database Write: The server-side function validates the data and inserts it into the appropriate Supabase table ( data_points or events) for the authenticated user.
5. User Confirmation: A success or failure message is returned to the chat interface.
#### 4.2. The Data Page: Explore & Correlate
- Purpose: To empower the user to become a researcher of their own health with powerful visualization tools.
- Composition: A large, interactive charting canvas is controlled by a panel for selecting metrics, date ranges, and aggregation levels. A raw data table below the chart displays the corresponding data points.
- Key Interactions: A Compare button to overlay a second metric for visual correlation, Chart Type toggles (line, bar, scatter), and an Export to CSV button.
#### 4.3. The Insights Page: Discover Your "Why"
- Purpose: To deliver the platform's core value by transforming raw data into personalized, actionable knowledge.
- Composition: A grid of "insight cards," each presenting a single, significant finding from the user's data with a clear title, a one-sentence summary, and a supporting mini-visualization.
- Key Interactions: A Generate New Insights button to manually trigger the analysis engine, filters to sort insights by category, and expandable cards that reveal detailed statistics about the analysis.
#### 5. Data Analysis & Insight Generation Strategy
The OptiHealth analysis engine is designed to operate in layers, providing insights of increasing sophistication by leveraging statistical methods, machine learning (ML), and time-series analysis.
#### 5.1. Foundational Analysis (The "What")
This layer provides a clear understanding of baseline metrics and trends using descriptive statistics, moving averages, and simple anomaly detection (e.g., z-scores) to flag unusual measurements.
#### 5.2. Relational & Correlative Analysis (The "Why")
This is the core of personal discovery, where OptiHealth connects the dots between different data streams.
- Correlation Analysis: Uses Pearson and Spearman correlation to find relationships between numeric metrics (e.g., "How does sunlight exposure correlate with sleep score?").
- Comparative Analysis: Uses statistical tests (t-test, ANOVA) to compare a metric across different conditions (e.g., "Is my resting heart rate lower on days after I run?").
- Semantic-Relational Analysis: Combines pgvector similarity search with quantitative data to answer contextual questions (e.g., "Show me my sleep quality on days I journaled about feeling anxious.").
#### 5.3. Predictive & Proactive Modeling (The "What If")
This advanced layer uses per-user machine learning models to forecast outcomes and identify the most influential factors in a user's health.
- Time-Series Forecasting: Employs models like ARIMA or Prophet to project future trends in key metrics.
- Personalized Feature Importance: Uses regression models (e.g., XGBoost) to rank the lifestyle factors that most strongly predict a key outcome (e.g., sleep score), delivering highly actionable insights.
#### 5.4. Unsupervised & Exploratory Analysis (The "Hidden Patterns")
This layer aims to discover structures in the user's data they might not know to look for.
- Clustering for "Day Type" Identification: Uses algorithms like K-Means to automatically group days into distinct archetypes (e.g., "High-Stress Workdays," "Active Recovery Days"), revealing hidden lifestyle patterns.
#### 6. Implementation of the Asynchronous Insight Engine
#### 6.1. Supabase Layer: Scheduler & Trigger
To perform computationally intensive analysis, OptiHealth uses a robust, asynchronous architecture centered around a database-driven job queue. This design ensures reliability and scalability.
1. Scheduling ( pg_cron): A pg_cron job is scheduled to run a master SQL function ( queue_all_user_analyses) on a regular basis (e.g., weekly).
2. Job Queue Population (SQL): This master function queries the auth.users table and inserts a new row into the public.analysis_jobs table for each active user. This entry, marked with a pending status, serves as a single unit of work. This approach is superior to direct webhooks as it is transactional and resilient to network failures.
3. Polling by the Worker (Python Service): The standalone Python Analysis Service periodically polls the analysis_jobs table for jobs with a pending status. When it finds one, it updates its status to in_progress to prevent other workers from picking up the same job.
4. Data Fetching & Analysis: The worker uses the user_id from the job to securely connect to the Supabase database and pull all necessary raw data. It then executes a suite of analysis functions using libraries like pandas, scikit-learn, and xgboost.
5. Storing Results: The generated insights are written back as structured JSON to the dedicated insights table, and the corresponding job in analysis_jobs is marked as completed. These results are now instantly available to the user in the web application.
#### 6.2. Python Analysis Service: The Worker
This is a standalone FastAPI application responsible for all heavy-duty data science tasks.
1. Receives Request: An endpoint ( /run-analysis) receives the user_id from the Supabase Edge Function.
2. Fetches Data: It connects directly to the Supabase PostgreSQL database using a secure connection string and pulls all necessary raw data for that user.
3. Performs Analysis: It executes a suite of analysis functions using libraries like pandas, scikit-learn, and xgboost.
4. Stores Results: The generated insights (as structured JSON) are written back to a dedicated insights table in the Supabase database, making them readily available for the user to view on the Insights page.