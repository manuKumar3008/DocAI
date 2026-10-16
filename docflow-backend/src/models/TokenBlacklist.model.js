const mongoose = require('mongoose');

const tokenBlacklistSchema = new mongoose.Schema({
  token: { type: String, required: true, unique: true },
  userId: { type: String, required: true },
  expiresAt: { type: Date, required: true },
}, { timestamps: true });

// MongoDB TTL index — auto-deletes expired tokens from collection
// No manual cleanup needed
tokenBlacklistSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });
tokenBlacklistSchema.index({ token: 1 }, { unique: true });

module.exports = mongoose.model('TokenBlacklist', tokenBlacklistSchema);