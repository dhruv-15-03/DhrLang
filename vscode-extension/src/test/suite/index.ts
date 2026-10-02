import * as path from 'path';
import Mocha from 'mocha';

/**
 * Runs inside the headless VS Code test host. Deliberately avoids a glob
 * dependency (only one spec file exists) to keep the test toolchain minimal.
 */
export function run(): Promise<void> {
    const mocha = new Mocha({
        ui: 'tdd',
        color: false,
        timeout: 60000
    });

    const testsRoot = path.resolve(__dirname);
    mocha.addFile(path.resolve(testsRoot, 'extension.test.js'));

    return new Promise((resolve, reject) => {
        try {
            mocha.run((failures) => {
                if (failures > 0) {
                    reject(new Error(`${failures} DhrLang extension integration test(s) failed.`));
                } else {
                    resolve();
                }
            });
        } catch (err) {
            reject(err);
        }
    });
}
