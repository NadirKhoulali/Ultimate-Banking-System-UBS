# Quick Reference - ATM Machine Texture Fix

## The Problem (1 minute summary)

Your block model is missing the **"particle"** texture key that Minecraft requires.

## The Fix (Copy & Paste Ready)

**File to edit:**
```
src/main/resources/assets/ultimatebankingsystem/models/block/atm_machine.json
```

**Find this (line 5-7):**
```json
"textures": {
	"1": "ultimatebankingsystem:block/atm_machine"
},
```

**Replace with this:**
```json
"textures": {
	"particle": "ultimatebankingsystem:block/atm_machine",
	"1": "ultimatebankingsystem:block/atm_machine"
},
```

**Save file → Rebuild (gradlew build) → Restart game → Done!**

---

## Why This Works

| Part | Purpose |
|------|---------|
| `"particle"` | Required by Minecraft for break particles, step sounds, etc. |
| `"1"` | Your element faces reference `"texture": "#1"` in the model |

Both need to reference the same texture file: `ultimatebankingsystem:block/atm_machine`

---

## What You Have vs What You Need

### Current (BROKEN)
```json
{
	"format_version": "1.21.6",
	"credit": "Made with Blockbench",
	"texture_size": [128, 128],
	"textures": {
		"1": "ultimatebankingsystem:block/atm_machine"          ← Missing particle!
	},
	...
}
```

### After Fix (WORKING)
```json
{
	"format_version": "1.21.6",
	"credit": "Made with Blockbench",
	"texture_size": [128, 128],
	"textures": {
		"particle": "ultimatebankingsystem:block/atm_machine",  ← Added!
		"1": "ultimatebankingsystem:block/atm_machine"
	},
	...
}
```

---

## All Models in Your Project

```
models/
├── block/
│   └── atm_machine.json          ← BROKEN (needs fix)
└── item/
    ├── atm_machine.json          ← Working (uses block as parent)
    └── cash.json                 ← Working (uses minecraft:item/generated)
```

**Important:** Item model uses your block as parent, so fixing the block fixes the item too!

---

## Texture File Status

✅ **Texture file EXISTS:**
- Path: `src/main/resources/assets/ultimatebankingsystem/textures/block/atm_machine.png`
- Size: 29 KB
- Status: Valid PNG

**No texture file issues!** The problem is only the missing "particle" key in the model JSON.

---

## 60-Second Fix Checklist

- [ ] Open `src/main/resources/assets/ultimatebankingsystem/models/block/atm_machine.json`
- [ ] Find the `"textures"` section (around line 5)
- [ ] Add `"particle": "ultimatebankingsystem:block/atm_machine",` as first texture
- [ ] Save file
- [ ] Run `gradlew build`
- [ ] Restart Minecraft
- [ ] Place ATM machine block
- [ ] ✅ See texture! (no more pink/magenta)

---

## If It Still Doesn't Work

1. **Check the log** - Look for atm_machine errors
2. **Verify JSON** - Make sure JSON is still valid (use jsonlint.com)
3. **Full rebuild** - Run `gradlew clean build`
4. **Fresh launch** - Delete old run configs, regenerate
5. **Texture file** - Open PNG in image viewer to verify it's valid

---

## Reference: Working Models

### Item Model (Simple)
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "ultimatebankingsystem:item/cash"
  }
}
```

### Item Model (References Block)
```json
{
  "parent": "ultimatebankingsystem:block/atm_machine"
}
```

**Key difference:** These models have a `"parent"` field. Your block model doesn't need it for custom shapes, but it's good practice to add `"parent": "minecraft:block/block"`.

---

## Common Questions

**Q: Why "particle" and not something else?**
A: Minecraft specifically requires this key in block models. It defines which texture to use for particle effects.

**Q: Does the texture file need a special name?**
A: No, it's just referenced in the model. The name `atm_machine.png` is fine.

**Q: Will this break anything?**
A: No, you're just adding a missing required field. It only fixes things.

**Q: Why did Blockbench miss this?**
A: Blockbench exports custom models but sometimes omits optional fields like "particle" for complex models. Manual addition is needed.

**Q: Does the item model need fixing too?**
A: No! The item model just references your block model as parent, so fixing the block fixes the item automatically.

---

## Full Current File (Just Textures Section)

**From line 4 to line 8 currently:**
```json
	"texture_size": [128, 128],
	"textures": {
		"1": "ultimatebankingsystem:block/atm_machine"
	},
	"elements": [
```

**Should be:**
```json
	"texture_size": [128, 128],
	"textures": {
		"particle": "ultimatebankingsystem:block/atm_machine",
		"1": "ultimatebankingsystem:block/atm_machine"
	},
	"elements": [
```

---

## After You Fix It

1. ✅ Block renders with correct texture in world
2. ✅ Block shows texture in inventory  
3. ✅ Block break particles show correct texture
4. ✅ Item (uses block as parent) displays correctly
5. ✅ No pink/magenta missing texture squares
6. ✅ No console errors about missing textures

---

## Need More Details?

- **MODEL_ANALYSIS.md** - Full technical analysis
- **MODELS_COMPARISON.md** - Side-by-side comparisons  
- **EXACT_FIX_NEEDED.md** - Detailed fix instructions
- **SEARCH_RESULTS_SUMMARY.md** - Complete search results

All files are in your project root.

---

## TL;DR

**Problem:** Block model missing "particle" texture key  
**Solution:** Add one line to textures section  
**Result:** Block shows texture instead of pink/magenta  
**Time:** 2 minutes  

Do it now! 👍

