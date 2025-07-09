-- H2 Steam Game Database Schema
-- UTF-8 compatibility and original ID preservation
SET MODE MySQL;

-- 1. tags table (maintaining AUTO_INCREMENT, using explicit ID during migration)
CREATE TABLE IF NOT EXISTS tags (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    name VARCHAR(255) NOT NULL UNIQUE
    );

-- 2. games table (maintaining AUTO_INCREMENT)
CREATE TABLE IF NOT EXISTS games (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     appid BIGINT NOT NULL UNIQUE,
                                     name VARCHAR(255) NOT NULL,
    description CLOB,
    image_url VARCHAR(255),
    korean_support BOOLEAN DEFAULT FALSE,
    is_free BOOLEAN DEFAULT FALSE,
    review_count INTEGER
    );

-- 3. reviews table (maintaining AUTO_INCREMENT)
CREATE TABLE IF NOT EXISTS reviews (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       appid BIGINT NOT NULL UNIQUE,
                                       total_reviews INTEGER DEFAULT 0,
                                       positive_review_text CLOB,
                                       negative_review_text CLOB,
                                       review_score VARCHAR(50),
    positive_percentage DECIMAL(5,2),
    positive_game_hour DECIMAL(7,2) DEFAULT 0.00,
    negative_game_hour DECIMAL(7,2) DEFAULT 0.00
    );

-- 4. game_tags table (foreign key references)
CREATE TABLE IF NOT EXISTS game_tags (
                                         game_id BIGINT NOT NULL,
                                         tag_id BIGINT NOT NULL,
                                         PRIMARY KEY (game_id, tag_id),
    FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
    );

-- 5. tag_cooccurrence table
CREATE TABLE IF NOT EXISTS tag_cooccurrence (
                                                tag1 VARCHAR(255) NOT NULL,
    tag2 VARCHAR(255) NOT NULL,
    count INTEGER NOT NULL,
    PRIMARY KEY (tag1, tag2)
    );

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_games_appid ON games(appid);
CREATE INDEX IF NOT EXISTS idx_games_korean_support ON games(korean_support);
CREATE INDEX IF NOT EXISTS idx_games_is_free ON games(is_free);
CREATE INDEX IF NOT EXISTS idx_reviews_appid ON reviews(appid);
CREATE INDEX IF NOT EXISTS idx_reviews_total_reviews ON reviews(total_reviews);
CREATE INDEX IF NOT EXISTS idx_game_tags_game_id ON game_tags(game_id);
CREATE INDEX IF NOT EXISTS idx_game_tags_tag_id ON game_tags(tag_id);