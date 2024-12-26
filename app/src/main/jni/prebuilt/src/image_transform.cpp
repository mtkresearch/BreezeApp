#include "image_transform.h"

#include <opencv2/opencv.hpp>

namespace mtk::image_utils {

void normalize(cv::Mat& image, const float* mean, const float* std) {
    if (image.channels() == 3 && image.type() != 21) {
        image.convertTo(image, CV_32F);
    }
    const int rows = image.rows;
    const int cols = image.cols;
    cv::Mat img_mean(rows, cols, CV_32FC3, cv::Scalar(mean[0], mean[1], mean[2]));
    cv::Mat img_std(rows, cols, CV_32FC3, cv::Scalar(std[0], std[1], std[2]));
    image -= img_mean;
    image /= img_std;
}

void resize(cv::Mat& image, const int size, cv::InterpolationFlags interpolation) {
    // TODO: Might need to support adaptive. Reference to
    // transformers.image_transform.get_resize_output_image_size
    const int rows = image.rows;
    const int cols = image.cols;
    int short_e, long_e;
    if (rows <= cols) {
        short_e = rows;
        long_e = cols;
    } else {
        short_e = cols;
        long_e = rows;
    }
    const int new_short = size;
    const int new_long = size * long_e / short_e;
    int new_cols, new_rows;
    if (rows <= cols) {
        new_cols = new_long;
        new_rows = new_short;
    } else {
        new_cols = new_short;
        new_rows = new_long;
    }
    cv::Size newsize(new_cols, new_rows);
    cv::resize(image, image, newsize, 0, 0, interpolation);
    image.convertTo(image, CV_8U);
}

void center_crop(cv::Mat& image, const int* crop_size) {
    const int rows = image.rows;
    const int cols = image.cols;
    const int crop_rows = crop_size[0];
    const int crop_cols = crop_size[1];
    int top = (rows - crop_rows) / 2;
    int bottom = top + crop_rows;
    int left = (cols - crop_cols) / 2;
    int right = left + crop_cols;

    if (top >= 0 && bottom <= rows && left >= 0 && right <= cols) {
        // If cropped area is within image boundaries
        image = image(cv::Range(top, bottom), cv::Range(left, right));
    } else {
        // If image is too small, pad it with zero...
        int new_rows = std::max(crop_rows, rows);
        int new_cols = std::max(crop_cols, cols);
        int top_pad = (new_rows - rows) / 2;
        int bottom_pad = top_pad + rows;
        int left_pad = (new_cols - cols) / 2;
        int right_pad = left_pad + cols;
        cv::copyMakeBorder(image, image, top_pad, bottom_pad, left_pad, right_pad,
                           cv::BORDER_CONSTANT, cv::Scalar(0, 0, 0));

        top += top_pad;
        bottom += top_pad;
        left += left_pad;
        right += left_pad;
        image = image(cv::Range(std::max(0, top), std::min(new_rows, bottom)),
                      cv::Range(std::max(0, left), std::min(new_cols, right)));
    }
}

void rescale(cv::Mat& image, const float scale) {
    if (image.channels() == 3 && image.type() != 21) {
        image.convertTo(image, CV_32F);
    }
    image *= scale;
}

cv::Mat clip_preprocess(std::string img_path, int& imageSizeBytes, const int size,
                        const int* crop_size, const float scale, const float* mean,
                        const float* std, cv::InterpolationFlags interpolation) {
    cv::Mat image = cv::imread(img_path, cv::IMREAD_COLOR);
    image.convertTo(image, CV_32F);
    resize(image, size, interpolation);
    center_crop(image, crop_size);
    rescale(image, scale);
    normalize(image, mean, std);
    if (!image.isContinuous()) {
        image = image.clone();
    }
    imageSizeBytes = image.total() * image.elemSize();
    return image;
}

} // namespace mtk::image_utils