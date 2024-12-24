#pragma once

#include <opencv2/opencv.hpp>

namespace mtk::image_utils {

// BGR format
constexpr float32_t kOpenAICLIPMean[3] = {0.40821073, 0.4578275, 0.48145466};
constexpr float32_t kOpenAICLIPStd[3] = {0.27577711, 0.26130258, 0.26862954};
constexpr int32_t kImgSize = 336;
constexpr int32_t kCropSize[2] = {336, 336};
constexpr float32_t kScale = 0.00392156862745098; // 1 / 255

void normalize(cv::Mat& image, const float* mean, const float* std);

// resize should be bicubic in LLaVA
void resize(cv::Mat& image, const int size, cv::InterpolationFlags interpolation = cv::INTER_CUBIC);

void center_crop(cv::Mat& image, const int* crop_size);

void rescale(cv::Mat& image, const float scale);

cv::Mat clip_preprocess(std::string img_path, int& imageSizeBytes, const int size,
                        const int* crop_size, const float scale,
                        const float* mean = kOpenAICLIPMean, const float* std = kOpenAICLIPStd,
                        cv::InterpolationFlags interpolation = cv::INTER_CUBIC);

} // namespace mtk::image_utils