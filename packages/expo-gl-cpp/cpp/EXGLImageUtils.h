#pragma once

#ifdef __ANDROID__
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>
#endif
#ifdef __APPLE__
#include <OpenGLES/ES3/gl.h>
#endif

#include <jsi/jsi.h>
#include <vector>

GLuint bytesPerPixel(GLenum type, GLenum format);

void flipPixels(GLubyte *pixels, size_t bytesPerRow, size_t rows);

std::shared_ptr<uint8_t> loadImage(
    facebook::jsi::Runtime &runtime,
    const facebook::jsi::Value &jsPixels,
    int *fileWidth,
    int *fileHeight,
    int *fileComp);
