# Native dialog callback check

After compiling the mod and resolving the client runtime, run:

```powershell
py -X utf8 tools/client-dialog-smoke/run.py --java-home "C:/path/to/jdk-17"
```

The test compiles the production `AsyncFileDialogs` and hands both a selected
path and a cancelled result from a worker to a controlled client event loop.
It verifies callback thread ownership, result preservation, and that the dialog
flag remains set until the client receives the result and is cleared before
callbacks run. No Minecraft client or native dialog is opened.
