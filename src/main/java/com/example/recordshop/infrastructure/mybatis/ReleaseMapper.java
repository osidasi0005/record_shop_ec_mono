package com.example.recordshop.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Release/Pressing/genresに対応するSQLを宣言するMapperインターフェース。
 * 実際のSQL文は{@code resources/mapper/ReleaseMapper.xml}に書く(MyBatisは
 * JPAと違いSQLを自動生成しないため、単純なCRUDでも全て明示的に書く)。
 */
@Mapper
public interface ReleaseMapper {

    void insertRelease(ReleaseRow row);

    void insertGenre(@Param("releaseId") UUID releaseId, @Param("genre") String genre);

    void insertPressing(PressingRow row);

    ReleaseRow selectReleaseById(@Param("id") UUID id);

    List<GenreRow> selectGenresByReleaseId(@Param("releaseId") UUID releaseId);

    List<PressingRow> selectPressingsByReleaseId(@Param("releaseId") UUID releaseId);

    List<ReleaseRow> selectAllReleases();

    /**
     * findAll()をN+1にしないための一括取得用メソッド。
     * Release件数分ループしてgenresを取得する実装にすると、JPA版で見つかったのと
     * 全く同じN+1問題がMyBatisでも再現するため、常に「全件を1回で取得」を徹底する。
     */
    List<GenreRow> selectAllGenres();

    /** {@link #selectAllGenres()} と同じ理由で、pressingsも全件を1回で取得する。 */
    List<PressingRow> selectAllPressings();

    UUID selectReleaseIdByPressingId(@Param("pressingId") UUID pressingId);
}
