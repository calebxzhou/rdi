var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI')
var Opcodes = Java.type('org.objectweb.asm.Opcodes')
var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode')
var FieldNode = Java.type('org.objectweb.asm.tree.FieldNode')
var InsnList = Java.type('org.objectweb.asm.tree.InsnList')
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode')
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode')
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode')
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode')
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode')

var OGG_AUDIO_STREAM = 'com/mojang/blaze3d/audio/OggAudioStream'
var AUDIO_STREAM = 'net/minecraft/client/sounds/AudioStream'
var BRIDGE = 'calebxzhou/rdi/mc/client/sound/OggAudioStreamBridge'
var INPUT_DESC = 'Ljava/io/InputStream;'
var BUFFERED_INPUT_DESC = 'Ljava/io/BufferedInputStream;'
var AUDIO_FORMAT_DESC = 'Ljavax/sound/sampled/AudioFormat;'
var BUFFER_DESC = 'Ljava/nio/ByteBuffer;'
var AUDIO_STREAM_DESC = 'Lnet/minecraft/client/sounds/AudioStream;'

var DELEGATE_FIELD = 'rdi$delegate'
var CLOSED_FIELD = 'rdi$closed'

function fail(message) {
    throw new Error('RDI OggAudioStream CoreMod target mismatch: ' + message)
}

function countFields(classNode, name, descriptor) {
    var count = 0
    for (var i = 0; i < classNode.fields.size(); i++) {
        var field = classNode.fields.get(i)
        if (field.name == name && (descriptor == null || field.desc == descriptor)) {
            count++
        }
    }
    return count
}

function findMethods(classNode, name, descriptor) {
    var matches = []
    for (var i = 0; i < classNode.methods.size(); i++) {
        var method = classNode.methods.get(i)
        if (method.name == name && method.desc == descriptor) {
            matches.push(method)
        }
    }
    return matches
}

function findObjectInit(method) {
    var matches = []
    for (var i = 0; i < method.instructions.size(); i++) {
        var instruction = method.instructions.get(i)
        if (instruction.getOpcode() == Opcodes.INVOKESPECIAL &&
            instruction.owner == 'java/lang/Object' &&
            instruction.name == '<init>' &&
            instruction.desc == '()V') {
            matches.push(instruction)
        }
    }
    if (matches.length != 1) {
        fail('expected exactly one Object.<init> call, found ' + matches.length)
    }
    return matches[0]
}

function appendGetField(list, owner, name, descriptor) {
    list.add(new VarInsnNode(Opcodes.ALOAD, 0))
    list.add(new FieldInsnNode(Opcodes.GETFIELD, owner, name, descriptor))
}

function addDelegateRead(method, readMethod) {
    var vanilla = new LabelNode()
    var injected = new InsnList()
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new JumpInsnNode(Opcodes.IFNULL, vanilla))
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new VarInsnNode(Opcodes.ILOAD, 1))
    injected.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, AUDIO_STREAM, readMethod, '(I)' + BUFFER_DESC, true))
    injected.add(new InsnNode(Opcodes.ARETURN))
    injected.add(vanilla)
    method.instructions.insert(injected)
    method.maxStack = Math.max(method.maxStack, 2)
}

function addDelegateReadAll(method) {
    var vanilla = new LabelNode()
    var injected = new InsnList()
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new JumpInsnNode(Opcodes.IFNULL, vanilla))
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, 'readAll', '(' + AUDIO_STREAM_DESC + ')' + BUFFER_DESC, false))
    injected.add(new InsnNode(Opcodes.ARETURN))
    injected.add(vanilla)
    method.instructions.insert(injected)
    method.maxStack = Math.max(method.maxStack, 1)
}

function addDelegateClose(method) {
    var vanilla = new LabelNode()
    var done = new LabelNode()
    var injected = new InsnList()
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new JumpInsnNode(Opcodes.IFNULL, vanilla))
    appendGetField(injected, OGG_AUDIO_STREAM, CLOSED_FIELD, 'Z')
    injected.add(new JumpInsnNode(Opcodes.IFNE, done))
    appendGetField(injected, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC)
    injected.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, AUDIO_STREAM, 'close', '()V', true))
    injected.add(new VarInsnNode(Opcodes.ALOAD, 0))
    injected.add(new InsnNode(Opcodes.ICONST_1))
    injected.add(new FieldInsnNode(Opcodes.PUTFIELD, OGG_AUDIO_STREAM, CLOSED_FIELD, 'Z'))
    injected.add(done)
    injected.add(new InsnNode(Opcodes.RETURN))
    injected.add(vanilla)
    method.instructions.insert(injected)
    method.maxStack = Math.max(method.maxStack, 2)
}

function addOpusConstructorPath(method, inputField, audioFormatField, audioFormatMethod, superInit) {
    var local = method.maxLocals
    method.maxLocals = local + 1

    var continueLabel = new LabelNode()
    var injected = new InsnList()

    // Reuse local 1 so the untouched Vorbis constructor stores the buffered stream in input.
    injected.add(new VarInsnNode(Opcodes.ALOAD, 1))
    injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, 'bufferInput', '(' + INPUT_DESC + ')' + BUFFERED_INPUT_DESC, false))
    injected.add(new VarInsnNode(Opcodes.ASTORE, 1))

    injected.add(new VarInsnNode(Opcodes.ALOAD, 1))
    injected.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, 'openOpus', '(' + BUFFERED_INPUT_DESC + ')' + AUDIO_STREAM_DESC, false))
    injected.add(new VarInsnNode(Opcodes.ASTORE, local))

    // Null means the detector reset the stream and found a non-Opus OGG; execute vanilla.
    injected.add(new VarInsnNode(Opcodes.ALOAD, 0))
    injected.add(new VarInsnNode(Opcodes.ALOAD, local))
    injected.add(new FieldInsnNode(Opcodes.PUTFIELD, OGG_AUDIO_STREAM, DELEGATE_FIELD, AUDIO_STREAM_DESC))
    injected.add(new VarInsnNode(Opcodes.ALOAD, local))
    injected.add(new JumpInsnNode(Opcodes.IFNULL, continueLabel))

    // The early-return path must initialize the two final vanilla fields itself.
    injected.add(new VarInsnNode(Opcodes.ALOAD, 0))
    injected.add(new VarInsnNode(Opcodes.ALOAD, 1))
    injected.add(new FieldInsnNode(Opcodes.PUTFIELD, OGG_AUDIO_STREAM, inputField, INPUT_DESC))
    injected.add(new VarInsnNode(Opcodes.ALOAD, 0))
    injected.add(new VarInsnNode(Opcodes.ALOAD, local))
    injected.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, AUDIO_STREAM, audioFormatMethod, '()' + AUDIO_FORMAT_DESC, true))
    injected.add(new FieldInsnNode(Opcodes.PUTFIELD, OGG_AUDIO_STREAM, audioFormatField, AUDIO_FORMAT_DESC))
    injected.add(new InsnNode(Opcodes.RETURN))
    injected.add(continueLabel)

    method.instructions.insert(superInit, injected)
    method.maxStack = Math.max(method.maxStack, 2)
}

function transformOggAudioStream(classNode) {
    if (classNode.name != OGG_AUDIO_STREAM) {
        fail('unexpected class ' + classNode.name)
    }

    var inputField = ASMAPI.mapField('f_83748_')
    var audioFormatField = ASMAPI.mapField('f_83747_')
    var readMethod = ASMAPI.mapMethod('m_7118_')
    var readAllMethod = ASMAPI.mapMethod('m_83764_')
    var audioFormatMethod = ASMAPI.mapMethod('m_6206_')

    var constructors = findMethods(classNode, '<init>', '(' + INPUT_DESC + ')V')
    var reads = findMethods(classNode, readMethod, '(I)' + BUFFER_DESC)
    var readAlls = findMethods(classNode, readAllMethod, '()' + BUFFER_DESC)
    var closes = findMethods(classNode, 'close', '()V')
    var formats = findMethods(classNode, audioFormatMethod, '()' + AUDIO_FORMAT_DESC)

    if (countFields(classNode, inputField, INPUT_DESC) != 1 ||
        countFields(classNode, audioFormatField, AUDIO_FORMAT_DESC) != 1) {
        fail('expected one mapped input/audioFormat field')
    }
    if (countFields(classNode, DELEGATE_FIELD, null) != 0 || countFields(classNode, CLOSED_FIELD, null) != 0) {
        fail('delegate/closed fields already exist; refusing a second transformation')
    }
    if (constructors.length != 1 || reads.length != 1 || readAlls.length != 1 || closes.length != 1 || formats.length != 1) {
        fail('expected one constructor/read/readAll/close/getFormat method, found ' +
            constructors.length + '/' + reads.length + '/' + readAlls.length + '/' + closes.length + '/' + formats.length)
    }

    var superInit = findObjectInit(constructors[0])

    classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, DELEGATE_FIELD, AUDIO_STREAM_DESC, null, null))
    classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, CLOSED_FIELD, 'Z', null, null))

    addDelegateRead(reads[0], readMethod)
    addDelegateReadAll(readAlls[0])
    addDelegateClose(closes[0])
    addOpusConstructorPath(constructors[0], inputField, audioFormatField, audioFormatMethod, superInit)
    return classNode
}

function initializeCoreMod() {
    return {
        'rdi_ogg_audio_stream': {
            'target': {
                'type': 'CLASS',
                'name': 'com.mojang.blaze3d.audio.OggAudioStream'
            },
            'transformer': transformOggAudioStream
        }
    }
}
