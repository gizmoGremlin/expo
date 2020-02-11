#pragma once

#include <jsi/jsi.h>
#include <type_traits>

namespace jsi = facebook::jsi;

enum class WebGLMethod {
#define NATIVE_METHOD(name) name,
#define NATIVE_WEBGL2_METHOD(name) NATIVE_METHOD(name)
#include "EXGLNativeMethods.def"
#undef NATIVE_METHOD
#undef NATIVE_WEBGL2_METHOD
};

template <WebGLMethod T>
struct WebGLMethodNameMapping;
#define NATIVE_METHOD(name)                          \
  template <>                                        \
  struct WebGLMethodNameMapping<WebGLMethod::name> { \
    constexpr static std::string_view name = #name   \
  }
#define NATIVE_WEBGL2_METHOD(name) NATIVE_METHOD(name)
#include "EXGLNativeMethods.def"
#undef NATIVE_METHOD
#undef NATIVE_WEBGL2_METHOD
}
template <WebGLMethod T>
using WebGLMethodName<T> = WebGLMethodNameMapping::name;

template <typename T>
inline constexpr bool is_integral_v = std::is_integral_v<T> && !std::is_same_v<bool, T>;

template <typename T>
inline std::enable_if_t<!(is_integral_v<T> || std::is_floating_point_v<T>), T> unpackArg(
    jsi::Runtime &runtime,
    const jsi::Value *jsArgv);

//
// unpackArgs explicit specializations
//

template <>
inline bool unpackArg<bool>(jsi::Runtime &runtime, const jsi::Value *jsArgv) {
  if (jsArgv->isBool()) {
    return jsArgv->getBool();
  } else if (jsArgv->isNull() || jsArgv->isUndefined()) {
    return false;
  } else if (jsArgv->isNumber()) {
    return jsArgv->getNumber() != 0;
  }
  throw std::runtime_error("value is not a boolean");
}

template <>
inline jsi::Object unpackArg<jsi::Object>(jsi::Runtime &runtime, const jsi::Value *jsArgv) {
  return jsArgv->getObject(runtime);
}

template <>
inline jsi::Array unpackArg<jsi::Array>(jsi::Runtime &runtime, const jsi::Value *jsArgv) {
  return jsArgv->getObject(runtime).getArray(runtime);
}

template <>
inline jsi::TypedArrayBase unpackArg<jsi::TypedArrayBase>(
    jsi::Runtime &runtime,
    const jsi::Value *jsArgv) {
  return jsArgv->getObject(runtime).getTypedArray(runtime);
}

template <>
inline jsi::ArrayBuffer unpackArg<jsi::ArrayBuffer>(
    jsi::Runtime &runtime,
    const jsi::Value *jsArgv) {
  return jsArgv->getObject(runtime).getArrayBuffer(runtime);
}

//
// unpackArgs overloads
//

template <typename T>
inline std::enable_if_t<is_integral_v<T>, T> unpackArg(
    jsi::Runtime &runtime,
    const jsi::Value *jsArgv) {
  return jsArgv->asNumber(); // TODO: add api to jsi to handle integers more efficiently
}

template <typename T>
inline std::enable_if_t<std::is_floating_point_v<T>, T> unpackArg(
    jsi::Runtime &runtime,
    const jsi::Value *jsArgv) {
  return jsArgv->asNumber();
}

namespace methodHelper {

template <typename T>
struct Arg {
  const jsi::Value *ptr;
  T unpack(jsi::Runtime &runtime) {
    return unpackArg<T>(runtime, ptr);
  }
};

// Create tuple of arguments packped in helper class
// Wrapping is added to preserve mapping between type and pointer to jsi::Value
template <typename First, typename... T>
constexpr std::tuple<Arg<First>, Arg<T>...> toArgTuple(const jsi::Value *jsArgv) {
  if constexpr (sizeof...(T) >= 1) {
    return std::tuple_cat(std::make_tuple(Arg<First>{jsArgv}), toArgTuple<T...>(jsArgv + 1));
  } else {
    return std::make_tuple(Arg<First>{jsArgv});
  }
}

// We need to unpack this in separate step because unpackArg
// used in Arg class is not an constexpr.
template <typename Tuple, size_t... I>
auto unpackArgsTuple(jsi::Runtime &runtime, Tuple tuple, std::index_sequence<I...>) {
  return std::make_tuple(std::get<I>(tuple).unpack(runtime)...);
}

template <typename Tuple, typename F, size_t... I>
auto generateNativeMethodBind(F fn, Tuple tuple, std::index_sequence<I...>) {
  return std::bind(fn, std::get<I>(tuple)...);
}

} // namespace methodHelper

//
// unpackArgs is parsing arguments passed to function from JS
// e.g.
// auto [ arg1, arg2, arg3 ] = unpackArgs<int, string, js::Object>(runtime, jsArgv, argc)
//
template <typename... T>
inline std::tuple<T...> unpackArgs(jsi::Runtime &runtime, const jsi::Value *jsArgv, int argc) {
  auto argTuple = methodHelper::toArgTuple<T...>(jsArgv);
  return methodHelper::unpackArgsTuple(runtime, argTuple, std::make_index_sequence<sizeof...(T)>());
}

//
// binds js arguments passed in jsArgv to function fn,
// e.g.
// _WRAP_METHOD(scissor, 4) {
//   addToNextBatch(generateNativeMethod(runtime, glScissor, jsArgv, argc));
//   return nullptr;
// }
template <typename... T>
auto generateNativeMethod(jsi::Runtime &runtime, void fn(T...), const jsi::Value *jsArgv) {
  auto argTuple = unpackArgs<T...>(runtime, jsArgv, sizeof...(T));
  return methodHelper::generateNativeMethodBind(
      fn, argTuple, std::make_index_sequence<sizeof...(T)>());
}
