const router = require("express").Router();
const ctrl = require("../controllers/admin.controller");
const { protect } = require("../middleware/auth.middleware");
const { authorize } = require("../middleware/role.middleware");

router.use(protect);

// ─── User Management ───────────────────────────────────────
router.get("/users", authorize("ADMIN"), ctrl.listUsers);
router.post("/users", authorize("ADMIN"), ctrl.createUser);
router.get("/users/:userId", authorize("ADMIN"), ctrl.getUser);
router.put("/users/:userId", authorize("ADMIN"), ctrl.updateUser);
router.delete("/users/:userId", authorize("ADMIN"), ctrl.deleteUser);
router.put("/users/:userId/reset-password", authorize("ADMIN"), ctrl.resetPassword);

// ─── Document Types ────────────────────────────────────────
router.get("/document-types", authorize("ADMIN", "INDEXER"), ctrl.listDocumentTypes);
router.post("/document-types", authorize("ADMIN"), ctrl.createDocumentType);
router.get("/document-types/:type", authorize("ADMIN", "INDEXER"), ctrl.getDocumentType);
router.put("/document-types/:type", authorize("ADMIN"), ctrl.updateDocumentType);
router.delete("/document-types/:type", authorize("ADMIN"), ctrl.deleteDocumentType);

// ─── Confidence Threshold ──────────────────────────────────
router.get("/confidence-threshold", authorize("ADMIN"), ctrl.getThreshold);
router.put("/confidence-threshold", authorize("ADMIN"), ctrl.setThreshold);

module.exports = router;
