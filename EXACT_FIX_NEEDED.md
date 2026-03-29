# EXACT FIX NEEDED - ATM Machine Block Model

## The Problem
Your block model `src/main/resources/assets/ultimatebankingsystem/models/block/atm_machine.json` is missing the **"particle" texture definition**, which is required by Minecraft block models.

## Quick Summary

| Issue | Current | Should Be | Why |
|-------|---------|-----------|-----|
| Textures object | `{"1": "..."}` | `{"particle": "...", "1": "..."}` | Minecraft requires particle texture |
| Parent field | Missing | Optional but recommended | Best practice for block models |

---

## THE MINIMAL FIX

**File:** `src/main/resources/assets/ultimatebankingsystem/models/block/atm_machine.json`

**Location:** Lines 5-7

**Change FROM:**
```json
	"textures": {
		"1": "ultimatebankingsystem:block/atm_machine"
	},
```

**Change TO:**
```json
	"textures": {
		"particle": "ultimatebankingsystem:block/atm_machine",
		"1": "ultimatebankingsystem:block/atm_machine"
	},
```

That's it! Just add the `"particle"` line.

---

## RECOMMENDED FIX (Slightly Better)

If you want to follow Minecraft best practices, also add a parent model.

**Add this line after "format_version" (new line 3):**

```json
	"parent": "minecraft:block/block",
```

**Result:**
```json
{
	"format_version": "1.21.6",
	"parent": "minecraft:block/block",
	"credit": "Made with Blockbench",
	"texture_size": [128, 128],
	"textures": {
		"particle": "ultimatebankingsystem:block/atm_machine",
		"1": "ultimatebankingsystem:block/atm_machine"
	},
	...rest of file...
}
```

---

## Complete Fixed File

Here's what your complete fixed file should look like (only showing the important parts):

```json
{
	"format_version": "1.21.6",
	"parent": "minecraft:block/block",
	"credit": "Made with Blockbench",
	"texture_size": [128, 128],
	"textures": {
		"particle": "ultimatebankingsystem:block/atm_machine",
		"1": "ultimatebankingsystem:block/atm_machine"
	},
	"elements": [
		{
			"from": [0, 0, 0],
			"to": [16, 16, 16],
			"faces": {
				"north": {"uv": [0, 0, 2, 2], "texture": "#1"},
				"east": {"uv": [0, 2, 2, 4], "texture": "#1"},
				"south": {"uv": [2, 0, 4, 2], "texture": "#1"},
				"west": {"uv": [2, 2, 4, 4], "texture": "#1"},
				"up": {"uv": [2, 6, 0, 4], "texture": "#1"},
				"down": {"uv": [6, 0, 4, 2], "texture": "#1"}
			}
		},
		...rest of elements stay the same...
	],
	"display": {
		"thirdperson_righthand": {
			"rotation": [75, 45, 0],
			"translation": [0, 2.5, 0],
			"scale": [0.375, 0.375, 0.375]
		}
	},
	"groups": [
		{
			"name": "group",
			"origin": [7, 12, 0],
			"color": 0,
			"children": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
		}
	]
}
```

---

## After Making the Changes

1. **Save the file**
2. **Rebuild:** `gradlew build` (or click Gradle refresh in your IDE)
3. **Restart:** Close and reopen the Minecraft client with your mod running
4. **Test:** Place the ATM machine block and verify the texture shows

You should see the ATM machine texture instead of the pink/magenta "missing texture" color.

---

## Why This Works

- **"particle":** Tells Minecraft which texture to use for block particles (breaking, footsteps, etc.)
- **"parent": "minecraft:block/block":** Registers this as a proper block model format
- **The rest:** Your Blockbench elements and texture references are already correct!

---

## Comparison: Your Model vs. Working Models

### Your model (BROKEN):
```json
"textures": {
	"1": "ultimatebankingsystem:block/atm_machine"
}
```

### Cash item (WORKING):
```json
"parent": "minecraft:item/generated",
"textures": {
	"layer0": "ultimatebankingsystem:item/cash"
}
```

### ATM item (WORKING):
```json
"parent": "ultimatebankingsystem:block/atm_machine"
```

**Key takeaway:** Both working models have a `"parent"` field. Your block model is the only one missing it (and the particle texture).

---

## If This Doesn't Work

If you've made these changes and it still doesn't work:

1. Check the **Game Log** for errors mentioning `atm_machine`
2. Verify the texture file exists: `src/main/resources/assets/ultimatebankingsystem/textures/block/atm_machine.png`
3. Make sure it's a valid PNG file (open it in an image viewer)
4. Do a full gradle clean: `gradlew clean build`
5. Delete the run configuration and regenerate it

---

## Advanced: Alternative Parent Models

If `minecraft:block/block` causes issues, you can try:

- `"parent": "minecraft:block/cube"` - For simple cube blocks
- `"parent": "minecraft:block/cross"` - For cross-shaped blocks
- No parent at all - For complex custom models (but add particle!)

For your ATM machine custom model, `minecraft:block/block` is the safest choice.

