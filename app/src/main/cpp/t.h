//
// Created by zellius on 09.11.18.
//


#include <unsupported/Eigen/CXX11/Tensor>

#ifndef NDKAPPLICATION_T_H
#define NDKAPPLICATION_T_H

long ttt() {
    Eigen::Tensor<float, 4> M(10, 10, 10, 10);
    M.mean();
    return M.size();
}

#endif //NDKAPPLICATION_T_H
