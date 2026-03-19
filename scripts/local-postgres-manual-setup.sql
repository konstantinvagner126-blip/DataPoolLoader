drop schema if exists datapool_manual cascade;
create schema datapool_manual;

create table datapool_manual.source_1 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual.source_2 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual.source_3 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual.source_4 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual.source_5 (
    id bigint primary key,
    payload text not null,
    source_name text not null
);

create table datapool_manual.target_pool (
    id bigint not null,
    payload text not null,
    source_name text not null
);

insert into datapool_manual.source_1(id, payload, source_name) values
    (1, 'alpha-1', 'db1'),
    (2, 'alpha-2', 'db1'),
    (3, 'alpha-3', 'db1');

insert into datapool_manual.source_2(id, payload, source_name) values
    (11, 'beta-1', 'db2'),
    (12, 'beta-2', 'db2'),
    (13, 'beta-3', 'db2'),
    (14, 'beta-4', 'db2');

insert into datapool_manual.source_3(id, payload, source_name) values
    (21, 'gamma-1', 'db3'),
    (22, 'gamma-2', 'db3');

insert into datapool_manual.source_4(id, payload, source_name) values
    (31, 'delta-1', 'db4'),
    (32, 'delta-2', 'db4'),
    (33, 'delta-3', 'db4'),
    (34, 'delta-4', 'db4'),
    (35, 'delta-5', 'db4');

insert into datapool_manual.source_5(id, payload, source_name) values
    (41, 'epsilon-1', 'db5'),
    (42, 'epsilon-2', 'db5'),
    (43, 'epsilon-3', 'db5');
