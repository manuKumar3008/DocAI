require("dotenv").config();
const mongoose = require("mongoose");
const User = require("../models/User.model");
const { v4: uuidv4 } = require("uuid");

const seed = async () => {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("MongoDB connected");

    const existing = await User.findOne({ email: "admin@docflow.com" });
    if (existing) {
      console.log("Admin user already exists — skipping seed");
      process.exit(0);
    }

    const admin = new User({
      userId: `USR_${uuidv4().replace(/-/g, "").substring(0, 8).toUpperCase()}`,
      name: "Admin User",
      email: "admin@docflow.com",
      password: "Admin@1234",
      role: "ADMIN",
      active: true,
    });

    await admin.save();
    console.log("Admin user created successfully");
    console.log("Email:    admin@docflow.com");
    console.log("Password: Admin@1234");
    process.exit(0);
  } catch (err) {
    console.error("Seed failed:", err.message);
    process.exit(1);
  }
};

seed();
