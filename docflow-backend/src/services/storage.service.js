const fs = require("fs");
const path = require("path");

const UPLOAD_DIR = process.env.UPLOAD_DIR || "./uploads";

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// Get full path of a stored file
const getFilePath = (storagePath) => {
  return path.resolve(storagePath);
};

// Delete file from disk
const deleteFile = async (storagePath) => {
  try {
    const fullPath = path.resolve(storagePath);
    if (fs.existsSync(fullPath)) {
      fs.unlinkSync(fullPath);
    }
  } catch (err) {
    console.error(`Failed to delete file: ${storagePath}`, err.message);
  }
};

// Check if file exists
const fileExists = (storagePath) => {
  return fs.existsSync(path.resolve(storagePath));
};

module.exports = {
  getFilePath,
  deleteFile,
  fileExists,
  UPLOAD_DIR,
};
