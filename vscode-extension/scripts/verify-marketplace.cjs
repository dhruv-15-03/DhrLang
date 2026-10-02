const { setTimeout: delay } = require('node:timers/promises');
const metadata = require('../package.json');

async function main() {
    const extensionId = `${metadata.publisher}.${metadata.name}`;
    for (let attempt = 1; attempt <= 12; attempt++) {
        const response = await fetch('https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json;api-version=3.0-preview.1'
            },
            body: JSON.stringify({
                filters: [{ criteria: [{ filterType: 7, value: extensionId }] }],
                flags: 1
            }),
            signal: AbortSignal.timeout(15000)
        });
        if (!response.ok) {
            throw new Error(`Marketplace query returned HTTP ${response.status}`);
        }
        const result = await response.json();
        const extension = result.results?.[0]?.extensions?.[0];
        if (extension?.publisher?.publisherName === metadata.publisher
                && extension?.extensionName === metadata.name
                && extension.versions?.some(version => version.version === metadata.version)) {
            console.log(`Marketplace confirms ${extensionId} ${metadata.version}`);
            return;
        }
        console.log(`Waiting for Marketplace indexing (${attempt}/12): ${extensionId} ${metadata.version}`);
        if (attempt < 12) {
            await delay(10000);
        }
    }
    throw new Error(`Published version ${metadata.version} is not yet visible on the Marketplace`);
}

main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
});
