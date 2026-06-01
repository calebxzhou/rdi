package calebxzhou.rdi.common.exception

class RequestError(msg:String?, cause: Throwable?=null): Exception(msg,cause) {
}