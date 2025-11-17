package org.drinkless.tdlib;

/**
 * STUB: TDLib has been removed from this project.
 * 
 * This stub class exists ONLY to maintain compilation of existing code that references TdApi.
 * No actual TDLib functionality is available - all Telegram features are non-operational.
 * 
 * The TdLibReflection class checks for TDLib availability at runtime via reflection,
 * and will correctly detect that TDLib is not present, causing Telegram features to be disabled.
 * 
 * This stub should be considered technical debt and could be removed in a future refactoring
 * when Telegram-related code is fully extracted or rewritten.
 */
public class TdApi {
    
    // Base class for all TDLib objects
    public static class Object {
        public Object() {}
    }
    
    // Base class for all TDLib functions
    public static abstract class Function<R extends Object> extends Object {
        public Function() {}
    }
    
    // Message class stub
    public static class Message extends Object {
        public long id;
        public long chatId;
        public MessageContent content;
        public int date;
        
        public Message() {}
        
        public Message(long id, long chatId, MessageContent content, int date) {
            this.id = id;
            this.chatId = chatId;
            this.content = content;
            this.date = date;
        }
    }
    
    // MessageContent base class
    public static abstract class MessageContent extends Object {
        public MessageContent() {}
    }
    
    // File class stub
    public static class File extends Object {
        public int id;
        public int size;
        public int expectedSize;
        public LocalFile local;
        public RemoteFile remote;
        
        public File() {}
        
        public File(int id, int size, int expectedSize, LocalFile local, RemoteFile remote) {
            this.id = id;
            this.size = size;
            this.expectedSize = expectedSize;
            this.local = local;
            this.remote = remote;
        }
    }
    
    // LocalFile class stub
    public static class LocalFile extends Object {
        public String path;
        public boolean isDownloadingActive;
        public boolean isDownloadingCompleted;
        public int downloadedSize;
        
        public LocalFile() {}
        
        public LocalFile(String path, boolean isDownloadingActive, boolean isDownloadingCompleted, int downloadedSize) {
            this.path = path;
            this.isDownloadingActive = isDownloadingActive;
            this.isDownloadingCompleted = isDownloadingCompleted;
            this.downloadedSize = downloadedSize;
        }
    }
    
    // RemoteFile class stub
    public static class RemoteFile extends Object {
        public String id;
        public String uniqueId;
        public boolean isUploadingActive;
        public boolean isUploadingCompleted;
        
        public RemoteFile() {}
    }
    
    // UpdateFile class stub
    public static class UpdateFile extends Object {
        public File file;
        
        public UpdateFile() {}
        
        public UpdateFile(File file) {
            this.file = file;
        }
    }
    
    // TdlibParameters class stub
    public static class TdlibParameters extends Object {
        public boolean useTestDc;
        public String databaseDirectory;
        public String filesDirectory;
        public boolean useFileDatabase;
        public boolean useChatInfoDatabase;
        public boolean useMessageDatabase;
        public boolean useSecretChats;
        public int apiId;
        public String apiHash;
        public String systemLanguageCode;
        public String deviceModel;
        public String systemVersion;
        public String applicationVersion;
        public boolean enableStorageOptimizer;
        public boolean ignoreFileNames;
        
        public TdlibParameters() {}
        
        public TdlibParameters(
            boolean useTestDc,
            String databaseDirectory,
            String filesDirectory,
            boolean useFileDatabase,
            boolean useChatInfoDatabase,
            boolean useMessageDatabase,
            boolean useSecretChats,
            int apiId,
            String apiHash,
            String systemLanguageCode,
            String deviceModel,
            String systemVersion,
            String applicationVersion,
            boolean enableStorageOptimizer,
            boolean ignoreFileNames
        ) {
            this.useTestDc = useTestDc;
            this.databaseDirectory = databaseDirectory;
            this.filesDirectory = filesDirectory;
            this.useFileDatabase = useFileDatabase;
            this.useChatInfoDatabase = useChatInfoDatabase;
            this.useMessageDatabase = useMessageDatabase;
            this.useSecretChats = useSecretChats;
            this.apiId = apiId;
            this.apiHash = apiHash;
            this.systemLanguageCode = systemLanguageCode;
            this.deviceModel = deviceModel;
            this.systemVersion = systemVersion;
            this.applicationVersion = applicationVersion;
            this.enableStorageOptimizer = enableStorageOptimizer;
            this.ignoreFileNames = ignoreFileNames;
        }
    }
    
    // SetTdlibParameters function stub
    public static class SetTdlibParameters extends Function<Object> {
        public TdlibParameters parameters;
        
        public SetTdlibParameters() {}
        
        public SetTdlibParameters(TdlibParameters parameters) {
            this.parameters = parameters;
        }
    }
    
    // Error class stub
    public static class Error extends Object {
        public int code;
        public String message;
        
        public Error() {}
        
        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}
