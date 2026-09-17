import sharp from 'sharp';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Define the standard Android mipmap sizes
const ICON_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
};

async function generateRoundedIcons() {
    const inputImagePath = path.join(__dirname, 'temp_logo.png'); // Using downloaded placeholder
    const outputBaseDir = path.join(__dirname, 'app', 'src', 'main', 'res');

    if (!fs.existsSync(inputImagePath)) {
        console.error(`Error: Source image not found at ${inputImagePath}`);
        process.exit(1);
    }

    console.log('Generating squircle rounded icons...');

    for (const [density, size] of Object.entries(ICON_SIZES)) {
        const radius = Math.floor(size * 0.22); 
        
        const roundedRectSvg = Buffer.from(
            `<svg><rect x="0" y="0" width="${size}" height="${size}" rx="${radius}" ry="${radius}"/></svg>`
        );

        const outDir = path.join(outputBaseDir, `mipmap-${density}`);
        if (!fs.existsSync(outDir)) {
            fs.mkdirSync(outDir, { recursive: true });
        }

        const outFile = path.join(outDir, 'ic_launcher.png');

        try {
            await sharp(inputImagePath)
                .resize(size, size, {
                    fit: sharp.fit.cover,
                    position: sharp.strategy.attention
                })
                .composite([{
                    input: roundedRectSvg,
                    blend: 'dest-in'
                }])
                .png()
                .toFile(outFile);
            
            console.log(`Generated: ${outFile} (${size}x${size})`);
        } catch (err) {
            console.error(`Failed to generate ${density} icon:`, err);
        }
    }
    
    console.log('Icon generation complete!');
}

generateRoundedIcons();
